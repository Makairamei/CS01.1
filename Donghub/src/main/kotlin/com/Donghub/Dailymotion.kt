package com.Donghub

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URI

class Geodailymotion : Dailymotion() {
    override val name = "GeoDailymotion"
    override val mainUrl = "https://geo.dailymotion.com"
}

open class Dailymotion : ExtractorApi() {
    override val mainUrl = "https://www.dailymotion.com"
    override val name = "Dailymotion"
    override val requiresReferer = false
    private val baseUrl = "https://www.dailymotion.com"

    private val videoIdRegex = "^[kx][a-zA-Z0-9]+$".toRegex()

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val embedUrl = getEmbedUrl(url) ?: return
        val id = getVideoId(embedUrl) ?: return
        val metaDataUrl = "$baseUrl/player/metadata/video/$id"
        val response = app.get(metaDataUrl, referer = embedUrl).text
        val subtitlesRegex = Regex(""""subtitles"\s*:\s*\{[^}]*"data"\s*:\s*(\[[^\]]*\])""")

        // Extract ONLY URLs inside the "qualities" block. The metadata JSON also
        // contains advertising URLs (ad_url / ad_error_url under "advertising")
        // that match a generic `"url":"..."` regex and would yield a silent /
        // broken stream. We isolate the qualities block first.
        val qStart = response.indexOf("\"qualities\"")
        val qBlock = if (qStart >= 0) {
            val candidates = listOf("\"reporting\"", "\"sharing\"", "\"subtitles\"", "\"info\"")
                .map { response.indexOf(it, qStart) }
                .filter { it > qStart }
            val qEnd = candidates.min() ?: response.length
            response.substring(qStart, qEnd)
        } else response

        val qualityUrlRegex = Regex(""""url"\s*:\s*"([^"]+\.m3u8[^"]*)"""")
        val urls = qualityUrlRegex.findAll(qBlock)
            .map { it.groupValues[1].replace("\\/", "/") }
            .filter { !it.contains("dmxleo.") }
            .distinct()
            .toList()

        urls.forEach { videoUrl ->
            getStream(videoUrl, this.name, callback)
        }

        val subtitlesMatches = subtitlesRegex.findAll(response).map { it.groupValues[1] }.toList()
        subtitlesMatches.forEach { subtitleJson ->
            val subRegex = Regex("""\{\s*"label"\s*:\s*"([^"]+)",\s*"urls"\s*:\s*\["([^"]+)"""")
            subRegex.findAll(subtitleJson).forEach { match ->
                val label = match.groupValues[1]
                val subUrl = match.groupValues[2]
                subtitleCallback(SubtitleFile(label, subUrl))
            }
        }
    }

    private fun getEmbedUrl(url: String): String? {
        if (url.contains("/embed/") || url.contains("/video/")) return url
        if (url.contains("geo.dailymotion.com")) {
            val videoId = url.substringAfter("video=")
            return "$baseUrl/embed/video/$videoId"
        }
        return null
    }

    private fun getVideoId(url: String): String? {
        val path = URI(url).path
        val id = path.substringAfter("/video/")
        return if (id.matches(videoIdRegex)) id else null
    }

    private suspend fun getStream(
        streamLink: String,
        name: String,
        callback: (ExtractorLink) -> Unit
    ) {
        // Important: Dailymotion master.m3u8 now ships video & audio as separate
        // renditions. We must pass the MASTER playlist URL directly so ExoPlayer
        // handles the #EXT-X-MEDIA audio tracks. Using generateM3u8() expands it
        // into variant (video-only) streams which lose the audio track.
        callback.invoke(
            newExtractorLink(
                source = name,
                name = name,
                url = streamLink,
                type = ExtractorLinkType.VIDEO
            ) {
                this.referer = baseUrl
                this.quality = Qualities.Unknown.value
            }
        )
    }
}
