package com.Animexin

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.extractors.Dailymotion

@CloudstreamPlugin
class AnimexinProvider: Plugin() {
    override fun load(context: Context) {
        LicenseClient.init(context)
        registerMainAPI(Animexin())
        registerExtractorAPI(Vtbe())
        registerExtractorAPI(waaw())
        registerExtractorAPI(wishfast())
        registerExtractorAPI(FileMoonSx())
        registerExtractorAPI(Dailymotion())
    }
}