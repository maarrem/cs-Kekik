// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup

class DiziMom : MainAPI() {
    override var mainUrl              = "https://www.dizimom.bar"
    override var name                 = "DiziMom"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 50L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 50L  // ? 0.05 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }
    
    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler/page/"        to "Son Bölümler",
        "${mainUrl}/yerli-dizi-izle/page/"     to "Yerli Diziler",
        "${mainUrl}/yabanci-dizi-izle/page/"   to "Yabancı Diziler",
        "${mainUrl}/tv-programlari-izle/page/" to "TV Programları",
        // "${mainUrl}/turkce-dublaj-diziler/page/"      to "Dublajlı Diziler",   // ! "Son Bölümler" Ana sayfa yüklenmesini yavaşlattığı için bunlar devre dışı bırakılmıştır..
        // "${mainUrl}/netflix-dizileri-izle/page/"      to "Netflix Dizileri",
        // "${mainUrl}/kore-dizileri-izle/page/"         to "Kore Dizileri",
        // "${mainUrl}/full-hd-hint-dizileri-izle/page/" to "Hint Dizileri",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}/", interceptor = interceptor).document
        val home     = if (request.data.contains("/tum-bolumler/")) {
            document.select("div.episode-box").mapNotNull { it.sonBolumler() } 
        } else {
            document.select("div.single-item").mapNotNull { it.diziler() }
        }

        return newHomePageResponse(request.name, home)
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name      = this.selectFirst("div.episode-name a")?.text()?.substringBefore(" izle") ?: return null
        val title     = name.replace(".Sezon ", "x").replace(".Bölüm", "")

        val epHref   = fixUrlNull(this.selectFirst("div.episode-name a")?.attr("href")) ?: return null
        val epDoc    = app.get(epHref).document
        val href     = epDoc.selectFirst("div#benzerli a")?.attr("href") ?: return null

        val posterUrl = fixUrlNull(this.selectFirst("a img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst("div.categorytitle a")?.text()?.substringBefore(" izle") ?: return null
        val href      = fixUrlNull(this.selectFirst("div.cat-img a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.cat-img img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}", interceptor = interceptor).document

        return document.select("div.single-item").mapNotNull { it.diziler() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title       = document.selectFirst("div.title h1")?.text()?.substringBefore(" izle") ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.category_image img")?.attr("src")) ?: return null
        val year        = document.selectXpath("//div[span[contains(text(), 'Yapım Yılı')]]").text().substringAfter("Yapım Yılı : ").trim().toIntOrNull()
        val description = document.selectFirst("div.category_desc")?.text()?.trim()
        val tags        = document.select("div.genres a").mapNotNull { it.text().trim() }
        val rating      = document.selectXpath("//div[span[contains(text(), 'IMDB')]]").text().substringAfter("IMDB : ").trim().toRatingInt()
        val actors      = document.selectXpath("//div[span[contains(text(), 'Oyuncular')]]").text().substringAfter("Oyuncular : ").split(", ").map {
            Actor(it.trim())
        }

        val episodes    = document.select("div.bolumust").mapNotNull {
            val epName    = it.selectFirst("div.baslik")?.text()?.trim() ?: return@mapNotNull null
            val epHref    = fixUrlNull(it.selectFirst("a")?.attr("href")) ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+)\.Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            val epSeason  = Regex("""(\d+)\.Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name    = epName.substringBefore(" izle").replace(title, "").trim()
                this.season  = epSeason
                this.episode = epEpisode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.rating    = rating
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZM", "data » $data")

        val ua = mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36")

        app.post(
            "${mainUrl}/wp-login.php",
            headers = ua,
            referer = "${mainUrl}/",
            data    = mapOf(
                "log"         to "keyiflerolsun",
                "pwd"         to "12345",
                "rememberme"  to "forever",
                "redirect_to" to mainUrl,
            )
        )

        val document = app.get(data, headers=ua, interceptor = interceptor).document

        val iframes     = mutableListOf<String>()
        val mainIframe = document.selectFirst("div.video p iframe")?.attr("src") ?: return false
        iframes.add(mainIframe)

        document.select("div.sources a").forEach {
            val subDocument = app.get(it.attr("href"), headers=ua, interceptor = interceptor).document
            val subIframe   = subDocument.selectFirst("div.video p iframe")?.attr("src") ?: return@forEach

            iframes.add(subIframe)
        }

        for (iframe in iframes) {
            Log.d("DZM", "iframe » $iframe")
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}
