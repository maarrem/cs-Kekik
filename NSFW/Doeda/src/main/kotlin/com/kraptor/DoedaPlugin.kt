// ! Bu araç @Kraptor123 tarafından | @Cs-GizliKeyif için yazılmıştır.
package com.kraptor

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.plugins.BasePlugin

@CloudstreamPlugin
class DoedaPlugin: BasePlugin() {
    override fun load() {
        registerMainAPI(Doeda())
        registerExtractorAPI(JetPlayer())
    }
}