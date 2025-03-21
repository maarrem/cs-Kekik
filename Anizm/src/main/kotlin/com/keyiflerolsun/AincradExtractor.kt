// ! Bu araç @kraptor123 tarafından yazılmıştır.

package com.keyiflerolsun.extractors

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.network.*
import okhttp3.*
import java.net.URI

class AincradExtractor : ExtractorApi() {
    override val name        = "Aincrad"
    override val mainUrl     = "https://anizmplayer.com"
    override val requiresReferer = true

    enum class Qualities(val qualityValue: Int) {
        Unknown(0),
        Low(1),
        Medium(2),
        High(3)
    }

    override suspend fun getUrl(url: String, referer: String?): List<ExtractorLink>? {
        val hash = URI(url).path.substringAfterLast("/")
        val postUrl = "$mainUrl/player/index.php?data=$hash&do=getVideo"
        
        val response = app.post(
            postUrl,
            data = mapOf(
                "hash" to hash,
                "r" to "https://anizm.net/"
            ),
            headers = mapOf(
                "Origin" to mainUrl,
                "X-Requested-With" to "XMLHttpRequest"
            )
        ).parsedSafe<AincradResponse>()

        return response?.securedLink?.let { hlsUrl ->
            listOf(
                ExtractorLink(
                    source = name,
                    name = name,
                    url = hlsUrl,
                    referer = "$mainUrl/",
                    quality = Qualities.Unknown.qualityValue, // Corrected here
                    isM3u8 = true
                )
            )
        } ?: emptyList()  // Return an empty list if response?.securedLink is null
    }

    private data class AincradResponse(
        @JsonProperty("securedLink") val securedLink: String?
    )
}
