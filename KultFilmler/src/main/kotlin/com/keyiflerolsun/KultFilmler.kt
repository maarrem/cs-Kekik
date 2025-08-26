// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.StringUtils.decodeUri
import okhttp3.Interceptor
import okhttp3.Response
import android.util.Base64
import org.jsoup.Jsoup
import java.util.regex.Pattern

class KultFilmler : MainAPI() {
    override var mainUrl              = "https://kultfilmler.pro"
    override var name                 = "KultFilmler"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries)
	
	    // ! CloudFlare bypass
    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/library/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 150L  // ? 0.15 saniye
    override var sequentialMainPageScrollDelay = 150L  // ? 0.15 saniye

    // ! CloudFlare v2
    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor      by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller): Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request  = chain.request()
            val response = chain.proceed(request)
            val doc      = Jsoup.parse(response.peekBody(1024 * 1024).string())

            if (doc.text().contains("Just a moment...")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/category/aile-filmleri-izle"		    to "Aile",
        "${mainUrl}/category/aksiyon-filmleri-izle"	        to "Aksiyon",
        "${mainUrl}/category/animasyon-filmleri-izle"	    to "Animasyon",
        "${mainUrl}/category/belgesel-izle"			        to "Belgesel",
        "${mainUrl}/category/bilim-kurgu-filmleri-izle"     to "Bilim Kurgu",
        "${mainUrl}/category/biyografi-filmleri-izle"	    to "Biyografi",
        "${mainUrl}/category/dram-filmleri-izle"		    to "Dram",
        "${mainUrl}/category/fantastik-filmleri-izle"	    to "Fantastik",
        "${mainUrl}/category/gerilim-filmleri-izle"	        to "Gerilim",
        "${mainUrl}/category/gizem-filmleri-izle"		    to "Gizem",
        "${mainUrl}/category/kara-filmleri-izle"		    to "Kara",
        "${mainUrl}/category/kisa-film-izle"			    to "Kısa Metrajlı",
        "${mainUrl}/category/komedi-filmleri-izle"		    to "Komedi",
        "${mainUrl}/category/korku-filmleri-izle"		    to "Korku",
        "${mainUrl}/category/macera-filmleri-izle"		    to "Macera",
        "${mainUrl}/category/muzik-filmleri-izle"		    to "Müzik",
        "${mainUrl}/category/polisiye-filmleri-izle"	    to "Polisiye",
        "${mainUrl}/category/politik-filmleri-izle"	        to "Politik",
        "${mainUrl}/category/romantik-filmleri-izle"	    to "Romantik",
        "${mainUrl}/category/savas-filmleri-izle"		    to "Savaş",
        "${mainUrl}/category/spor-filmleri-izle"		    to "Spor",
        "${mainUrl}/category/suc-filmleri-izle"		        to "Suç",
        "${mainUrl}/category/tarih-filmleri-izle"		    to "Tarih",
        "${mainUrl}/category/yerli-filmleri-izle"		    to "Yerli"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val movieBoxes = document.select("div.col-md-12 div.movie-box")
        val home = movieBoxes.mapNotNull { 
        it.toSearchResult() 
    }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.img img")?.attr("alt")?.takeIf { it.isNotEmpty() } ?: return null
        Log.d("toSearchResult", "Title: $title")

        val href = this.selectFirst("a")?.attr("href")?.let { fixUrlNull(it) } ?: return null
        val posterUrl = this.selectFirst("div.img img")?.attr("src")?.let { fixUrlNull(it) }

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = posterUrl }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=${query}").document

        return document.select("div.movie-box").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("div.film-bilgileri img")?.attr("alt")?.trim() ?: document.selectFirst("[property='og:title']")?.attr("content")?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description     = document.selectFirst("div.description")?.text()?.trim()
        var tags            = document.select("ul.post-categories a").map { it.text() }
        val rating          = document.selectFirst("div.imdb-count")?.text()?.trim()?.split(" ")?.first()?.toRatingInt()
        val year            = Regex("""(\d+)""").find(document.selectFirst("li.release")?.text()?.trim() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val duration        = Regex("""(\d+)""").find(document.selectFirst("li.time")?.text()?.trim() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val actors          = document.select("div.actors a").map {
            Actor(it.text())
        }

        if (url.contains("/dizi/")) {
            tags  = document.select("div.category a").map { it.text() }

            val episodes = document.select("div.episode-box").mapNotNull {
                val epHref    = fixUrlNull(it.selectFirst("div.name a")?.attr("href")) ?: return@mapNotNull null
                val ssnDetail = it.selectFirst("span.episodetitle")?.ownText()?.trim() ?: return@mapNotNull null
                val epDetail  = it.selectFirst("span.episodetitle b")?.ownText()?.trim() ?: return@mapNotNull null
                val epName    = "$ssnDetail - $epDetail"
                val epSeason  = ssnDetail.substringBefore(". ").toIntOrNull()
                val epEpisode = epDetail.substringBefore(". ").toIntOrNull()

                newEpisode(epHref) {
                    this.name    = epName
                    this.season  = epSeason
                    this.episode = epEpisode
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl       = poster
                this.year            = year
                this.plot            = description
                this.tags            = tags
                this.rating          = rating
                this.duration        = duration
                addActors(actors)
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.rating          = rating
            this.duration        = duration
            addActors(actors)
        }
    }

    private fun getIframe(sourceCode: String): String {
        // val atobKey = Regex("""atob\("(.*)"\)""").find(sourceCode)?.groupValues?.get(1) ?: return ""

        // return Jsoup.parse(String(Base64.decode(atobKey))).selectFirst("iframe")?.attr("src") ?: ""

        val atob = Regex("""PHA\+[0-9a-zA-Z+/=]*""").find(sourceCode)?.value ?: return ""

        val padding    = 4 - atob.length % 4
        val atobPadded = if (padding < 4) atob.padEnd(atob.length + padding, '=') else atob

        val iframe = Jsoup.parse(String(Base64.decode(atobPadded, Base64.DEFAULT), Charsets.UTF_8))

        return fixUrlNull(iframe.selectFirst("iframe")?.attr("src")) ?: ""
    }
private fun extractSubtitleUrl(sourceCode: String): String? {
    Log.d("KLT", "Source code length: ${sourceCode.length}")
    Log.d("KLT", "Source code contains 'playerjsSubtitle': ${sourceCode.contains("playerjsSubtitle")}")
    Log.d("KLT", "Source code contains '.srt': ${sourceCode.contains(".srt")}")
    
    // Basit pattern ile test
    val pattern = Pattern.compile("(https?://[^\"\\s]+\\.srt)")
    val matcher = pattern.matcher(sourceCode)
    
    if (matcher.find()) {
        val subtitleUrl = matcher.group(1)
        Log.d("KLT", "Found subtitle URL: $subtitleUrl")
        return subtitleUrl
    }
    
    // Bulunamazsa kaynak kodun bir kısmını logla
    Log.d("KLT", "First 500 chars of source: ${sourceCode.take(500)}")
    Log.d("KLT", "No subtitle URL found in source code")
    return null
}
       private suspend fun extractSubtitleFromIframe(iframeUrl: String): String? {
        if (iframeUrl.isEmpty()) return null
        try {
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36",
                "Referer" to mainUrl
            )
            val iframeResponse = app.get(iframeUrl, headers=headers)
            val iframeSource = iframeResponse.text
		Log.d("KLT", "iframeSource » $iframeSource")
            return extractSubtitleUrl(iframeSource)
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("KLT", "data » $data")
        val document = app.get(data).document
        val iframes  = mutableSetOf<String>()

        val mainFrame = getIframe(document.html())
        iframes.add(mainFrame)

        document.select("div.container#player").forEach {
            val alternatif = it.selectFirst("iframe")?.attr("src")
            if (alternatif != null) {
                val alternatifDocument = app.get(alternatif).document
                val alternatifFrame    = getIframe(alternatifDocument.html())
                iframes.add(alternatifFrame)
            }
        }

        for (iframe in iframes) {
            Log.d("KLT", "iframe » $iframe")
            if (iframe.contains("vidmoly")) {
                val headers  = mapOf(
                    "User-Agent"     to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36",
                    "Sec-Fetch-Dest" to "iframe"
                )
                val iSource = app.get(iframe, headers=headers, referer="${mainUrl}/").text
                val m3uLink = Regex("""file:"([^"]+)""").find(iSource)?.groupValues?.get(1) ?: throw ErrorLoadingException("m3u link not found")

                Log.d("Kekik_VidMoly", "m3uLink » $m3uLink")

                callback.invoke(
                    newExtractorLink(
                        source  = "VidMoly",
                        name    = "VidMoly",
                        url     = m3uLink,
                        type    = INFER_TYPE
            ) {
                quality = Qualities.Unknown.value
            }
                )
            } else {
            // Extract subtitle for other iframes
            val subtitleUrl = extractSubtitleFromIframe(data)
            if (subtitleUrl != null) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        lang = "Türkçe",
                        url = subtitleUrl
                    )
                )
            }
                loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
            }
        }

        return true
    }
}
