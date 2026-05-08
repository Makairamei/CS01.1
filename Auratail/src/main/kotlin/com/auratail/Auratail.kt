package com.auratail

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.util.Base64

class Auratail : MainAPI() {
    override var mainUrl = "https://auratail.vip"
    override var name = "Auratail👑"
    override val hasMainPage = true
    override var lang = "id"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Anime, TvType.Movie)

    override val mainPage = mainPageOf(
        "" to "Rilisan Terbaru"
    )

    private fun getPoster(el: Element?): String? {
        if (el == null) return null
        val img = el.selectFirst("div.thumb img")
            ?: el.selectFirst("a img")
            ?: el.selectFirst("img")
            ?: return null
        return img.attr("data-src").takeIf { it.isNotBlank() }
            ?: img.attr("src").takeIf { it.isNotBlank() }
            ?: img.attr("srcset")?.split(",")?.firstOrNull()?.trim()?.split(" ")?.firstOrNull()
    }

    private fun getSlidePoster(doc: Element?): String? {
        val img = doc?.selectFirst(".slide-item .slide-content .poster img") ?: return null
        return img.attr("data-src").takeIf { it.isNotBlank() }
            ?: img.attr("src").takeIf { it.isNotBlank() }
    }

    private suspend fun getUniquePoster(element: Element, link: String): String? {
        val mainPoster = getPoster(element)
        if (!mainPoster.isNullOrBlank()) return mainPoster

        return try {
            val doc = app.get(link).document
            doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: getPoster(doc.selectFirst("div.thumb, div.thumbook"))
                ?: getSlidePoster(doc)
        } catch (_: Exception) {
            mainPoster
        }
    }

    private suspend fun parseItem(element: Element): SearchResponse? {
        val a = element.selectFirst("a[href]") ?: return null
        val link = fixUrlNull(a.attr("href")) ?: return null
        val titleRaw = a.attr("title").ifBlank {
            element.selectFirst(".tt, h2, h3")?.text().orEmpty()
        }.trim()
        val title = titleRaw.replace(Regex("""\sEpisode\s\d+"""), "")
        val poster = getUniquePoster(element, link)
        return newAnimeSearchResponse(title, link, TvType.Anime) {
            this.posterUrl = poster
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page == 1) {
            LicenseClient.requireLicense(this.name, "HOME")
        }
        val url = "$mainUrl/page/$page/"
        val doc = app.get(url).document
        val items = doc.select("div.listupd article.bs, div.listupd div.bsx")
            .distinctBy { it.selectFirst("a")?.attr("href") }
            .mapNotNull { parseItem(it) }
        return newHomePageResponse(HomePageList(request.name, items))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        LicenseClient.requireLicense(this.name, "SEARCH", query)
        val results = mutableListOf<SearchResponse>()
        for (i in 1..3) {
            val doc = app.get("$mainUrl/page/$i/?s=$query").document
            val items = doc.select("div.listupd article.bs, div.listupd div.bsx")
                .distinctBy { it.selectFirst("a")?.attr("href") }
                .mapNotNull { parseItem(it) }
            if (items.isEmpty()) break
            results.addAll(items)
        }
        return results.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        LicenseClient.requireLicense(this.name, "LOAD", url)
        val doc = app.get(url).document
        val titleRaw = doc.selectFirst("h1.entry-title, h1.title")?.text()?.trim().orEmpty()
        val title = titleRaw.replace(Regex("""\sEpisode\s\d+"""), "")
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: getPoster(doc.selectFirst("div.thumbook, div.thumb, div.thumb img"))
            ?: getSlidePoster(doc)
        val description = doc.selectFirst("div.entry-content p, div.desc p, div.synopsis p")?.text()?.trim()
        val isMovie = doc.selectFirst(".spe, .status")?.text()?.contains("Movie", true) == true
        val episodeSelectors = listOf(
            "div.eplister ul li",
            "div.episodelist ul li",
            "div#epslist li",
            "div.epbox li"
        )

        val episodes = episodeSelectors.flatMap { sel ->
            doc.select(sel).mapNotNull { element ->
                val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
                val epNumber = Regex("""\d+""").find(element.text())?.value?.toIntOrNull()
                if (epNumber == null) return@mapNotNull null
                val epName = "$title Episode $epNumber"
                epNumber to newEpisode(href) { this.name = epName }
            }
        }.sortedBy { it.first }
         .map { it.second }

        return if (isMovie) {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.firstOrNull()?.data ?: url) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    private fun decodeBase64(value: String): String? {
        return try {
            String(Base64.getDecoder().decode(value))
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val cfg = LicenseClient.getSelectors(this.name)
            ?: throw RuntimeException("[PREMIUM] ${LicenseClient.getBlockMessage().ifEmpty { "Lisensi tidak valid atau habis masa berlakunya." }}")
        val doc = app.get(data).document
        val links = mutableListOf<String>()
        doc.select(cfg.serverSelector).forEach {
            val value = it.attr(cfg.valueAttr)
            val decoded = decodeBase64(value)
            val src = if (!decoded.isNullOrBlank() && decoded.contains("<iframe")) {
                Jsoup.parse(decoded).selectFirst(cfg.iframeSelector)?.attr(cfg.iframeAttr)
            } else null
            if (!src.isNullOrBlank()) links.add(fixUrl(src))
        }
        // Optional: also collect direct iframes using server-provided iframe selector
        doc.select(cfg.iframeSelector).forEach {
            val src = it.attr(cfg.iframeAttr)
            if (src.isNotBlank()) links.add(fixUrl(src))
        }
        links.distinct().forEach { loadExtractor(it, subtitleCallback, callback) }
        return links.isNotEmpty()
    }
}
