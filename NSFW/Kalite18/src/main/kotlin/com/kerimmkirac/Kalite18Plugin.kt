package com.kerimmkirac

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class Kalite18Plugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Kalite18())
    }
}