// ! Bu araç @kraptor123 tarafından yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import java.net.URLEncoder
import com.keyiflerolsun.getVideoUrls
import com.keyiflerolsun.extractors.AincradExtractor

class Anizm : MainAPI() {
    override var mainUrl              = "https://anizm.net"
    override var name                 = "Anizm"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)
    override var sequentialMainPage = true
    override var sequentialMainPageDelay       = 50L
    override var sequentialMainPageScrollDelay = 50L

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())
            if (doc.text().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }
            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/harf?harf=a&sayfa=" to "a",
        "${mainUrl}/harf?harf=b&sayfa=" to "b",
        "${mainUrl}/harf?harf=c&sayfa=" to "c",
        "${mainUrl}/harf?harf=d&sayfa=" to "d",
        "${mainUrl}/harf?harf=e&sayfa=" to "e",
        "${mainUrl}/harf?harf=f&sayfa=" to "f",
        "${mainUrl}/harf?harf=g&sayfa=" to "g",
        "${mainUrl}/harf?harf=h&sayfa=" to "h",
        "${mainUrl}/harf?harf=i&sayfa=" to "i",
        "${mainUrl}/harf?harf=j&sayfa=" to "j",
        "${mainUrl}/harf?harf=k&sayfa=" to "k",
        "${mainUrl}/harf?harf=l&sayfa=" to "l",
        "${mainUrl}/harf?harf=m&sayfa=" to "m",
        "${mainUrl}/harf?harf=n&sayfa=" to "n",
        "${mainUrl}/harf?harf=o&sayfa=" to "o",
        "${mainUrl}/harf?harf=p&sayfa=" to "p",
        "${mainUrl}/harf?harf=q&sayfa=" to "q",
        "${mainUrl}/harf?harf=r&sayfa=" to "r",
        "${mainUrl}/harf?harf=s&sayfa=" to "s",
        "${mainUrl}/harf?harf=t&sayfa=" to "t",
        "${mainUrl}/harf?harf=u&sayfa=" to "u",
        "${mainUrl}/harf?harf=v&sayfa=" to "v",
        "${mainUrl}/harf?harf=w&sayfa=" to "w",
        "${mainUrl}/harf?harf=x&sayfa=" to "x",
        "${mainUrl}/harf?harf=y&sayfa=" to "y",
        "${mainUrl}/harf?harf=z&sayfa=" to "z"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("a.pfull").mapNotNull { it.toMainPageResult() }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div.anizm_textUpper.anizm_textBold.truncateText")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val formattedQuery = query.lowercase().replace(" ", "-")
        val document = app.get("${mainUrl}/${formattedQuery}").document
        return document.select("div.ui.container.animeDetayContainer").mapNotNull { it.toSearchResult() }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("h2.anizm_pageTitle")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("a.anizm_colorDefault")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img.anizm_shadow.anizm_round.infoPosterImgItem")?.attr("src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title       = document.selectFirst("a.anizm_colorDefault")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("img.anizm_shadow.anizm_round.infoPosterImgItem")?.attr("src"))
        val description = document.selectFirst("div.infoDesc")?.text()?.trim()
        val year        = document.selectFirst("div.infoSta.mt-2 li")?.text()?.trim()?.toIntOrNull()
        val tags        = document.select("span.ui.label").map { it.text() }
        val rating      = document.selectFirst("g.circle-chart__info")?.text()?.trim()?.toRatingInt()
        val duration    = document.selectFirst("span.runtime")?.text()?.split(" ")?.first()?.trim()?.toIntOrNull()
        val recommendations = document.select("div.relatedAnimeContainer").mapNotNull { it.toRecommendationResult() }
        val trailer     = fixUrlNull(document.selectFirst("iframe.yt-hd-thumbnail")?.attr("src"))
        
        val episodes = document.select("div.bolumKutucugu").mapNotNull { episodeBlock ->
            val aTag = episodeBlock.selectFirst("a[href]") ?: return@mapNotNull null
            val epHref = fixUrlNull(aTag.attr("href")) ?: return@mapNotNull null
            val episodeText = aTag.selectFirst("div.episodeNo")?.text()
            val epEpisode = Regex("""(\d+)(?:\.|\s|\-)?\s*(?:Bolum)""", RegexOption.IGNORE_CASE)
                .find(episodeText ?: "")
                ?.groups?.get(1)
                ?.value
                ?.toIntOrNull()
            newEpisode(epHref) {
                this.episode = epEpisode
                this.name = if (epEpisode != null) "Bölüm $epEpisode" else "Bölüm"
            }
        }
        
        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl       = poster
            this.plot            = description
            this.year            = year
            this.tags            = tags
            this.rating          = rating
            this.duration        = duration
            this.recommendations = recommendations
            addTrailer(trailer)
        }
    }

    private fun Element.toRecommendationResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt") ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("data-src"))
        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("ANZM", "loadLinks - Başlangıç: data » $data")
    
    val videoLinks = getVideoUrls(data)
    Log.d("ANZM", "loadLinks - Çözülen video link sayısı: ${videoLinks.size}")

    videoLinks.forEach { (name, url) ->
        Log.d("ANZM", "loadLinks - İşlenen video: $name -> $url")
        
        when {
            url.contains("anizmplayer.com") -> {
                AincradExtractor().getUrl(url, mainUrl)?.forEach(callback) // Process Aincrad first
                return@forEach // Skip other extractors for this URL
            }
            else -> {
                loadExtractor(
                    url = url,
                    referer = mainUrl,
                    subtitleCallback = subtitleCallback,
                    callback = callback
                )
            }
        }
    }
    
    Log.d("ANZM", "loadLinks - Sonuç: ${videoLinks.isNotEmpty()}")
    return videoLinks.isNotEmpty()
}
}