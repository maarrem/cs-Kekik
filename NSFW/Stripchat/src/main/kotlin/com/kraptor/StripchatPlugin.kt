package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class StripchatPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Stripchat())
    }
}