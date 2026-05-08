package com.Animekhor

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup


class Donghuaword  : Animekhor() {
    override var mainUrl              = "https://donghuaworld.com"
    override var name                 = "Donghuaworld🕊"
    override val hasMainPage          = true
    override var lang                 = "id"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.Anime)

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val cfg = LicenseClient.getSelectors(this.name)
            ?: throw RuntimeException("[PREMIUM] ${LicenseClient.getBlockMessage().ifEmpty { "Lisensi tidak valid atau habis masa berlakunya." }}")
        val document = app.get(data).documentLarge
        document.select(cfg.serverSelector).map {
            val encoded=it.attr(cfg.valueAttr)
            val decodedUrl = base64Decode(encoded)
            val url = Jsoup.parse(decodedUrl).select(cfg.iframeSelector).attr(cfg.iframeAttr)
            loadExtractor(url, referer = mainUrl, subtitleCallback, callback)

        }
        return true
    }
}