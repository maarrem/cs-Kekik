

package com.nikyokki

import android.util.Log
import com.lagradost.cloudstream3.Actor
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.toRatingInt
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.nicehttp.cookies
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class RoketDizi : MainAPI() {
    override var mainUrl = "https://roketdizi.tv"
    override var name = "RoketDizi"
    override val hasMainPage = true
    override var lang = "tr"
    override val hasQuickSearch = false
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie)
    var cf_clearance = ""
    var ci_session = ""
    var level = ""
    var udys = ""

    // ! CloudFlare bypass
    override var sequentialMainPage =
        true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay = 250L  // ? 0.05 saniye
    override var sequentialMainPageScrollDelay = 250L  // ? 0.05 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/dizi/tur/aksiyon" to "Aksiyon",
        "${mainUrl}/dizi/tur/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi/tur/gerilim" to "Gerilim",
        "${mainUrl}/dizi/tur/fantastik" to "Fantastik",
        "${mainUrl}/dizi/tur/komedi" to "Komedi",
        "${mainUrl}/dizi/tur/korku" to "Korku",
        "${mainUrl}/dizi/tur/macera" to "Macera",
        "${mainUrl}/dizi/tur/suc" to "Suç",

        "${mainUrl}/film-kategori/animasyon" to "Aksiyon Film"
    )

    private val cloudflareKiller by lazy { CloudflareKiller() }
    private val interceptor by lazy { CloudflareInterceptor(cloudflareKiller) }

    class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            println("Chain: $chain")
            val request = chain.request()
            println("Request: $request")
            val response = chain.proceed(request)
            println("Request: $response")
            val doc = Jsoup.parse(response.peekBody(1024 * 1024).string())
            val intercept = cloudflareKiller.intercept(chain)
            println("Saved Cookies: ${cloudflareKiller.savedCookies}")
            println("Cookies: ${intercept.cookies}")
            return intercept

            //return response
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val mainReq = app.get("${request.data}?&page=${page}")

        val document = mainReq.document
        val home = document.select("a.w-full").mapNotNull { it.diziler() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title = this.selectFirst("h2")?.text() ?: return null
        val href = fixUrlNull(this.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return if (href.contains("/dizi/")) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun toSearchResponse(ad: String, link: String, posterLink: String): SearchResponse? {
        if (link.contains("dizi")) {
            return newTvSeriesSearchResponse(
                ad ?: return null,
                link,
                TvType.TvSeries,
            ) {
                this.posterUrl = posterLink
            }
        } else {
            return newMovieSearchResponse(
                ad ?: return null,
                link,
                TvType.Movie,
            ) {
                this.posterUrl = posterLink
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mainReq = app.get(mainUrl)
        val mainPage = mainReq.document
        val cKey = mainPage.selectFirst("input[name='cKey']")?.attr("value") ?: return emptyList()
        val cValue =
            mainPage.selectFirst("input[name='cValue']")?.attr("value") ?: return emptyList()
        val cookie = mainReq.cookies["PHPSESSID"].toString()
        println("Ckey: $cKey ---- Cvalue: $cValue ---- cookie: $cookie")

        val veriler = mutableListOf<SearchResponse>()

        val searchReq = app.post(
            "${mainUrl}/bg/searchcontent",
            data = mapOf(
                "cKey" to cKey,
                "cValue" to cValue,
                "searchterm" to query
            ),
            headers = mapOf(
                "Accept" to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest",
                "Cookie" to "PHPSESSID=$cookie"
            ),
            referer = "${mainUrl}/",
            cookies = mapOf(
                "CNT" to "vakTR",
                "PHPSESSID" to cookie
            )
        ).parsedSafe<SearchResult>()
        println("SearchReq: $searchReq")

        if (searchReq?.data?.state != true) {
            throw ErrorLoadingException("Invalid Json response")
        }

        val searchDoc = searchReq.data.html?.trim()

        searchDoc?.trim()?.split("</a>")?.forEach { item ->

            val bb = item.substringAfter("<a href=\"").substringBefore("\"")
            val diziUrl = bb.trim()
            val cc = item.substringAfter("data-srcset=\"").substringBefore(" 1x")
            val posterLink = cc.trim()
            val dd = item.substringAfter("<span class=\"text-white\">").substringBefore("</span>")
            val ad = dd.trim()
            toSearchResponse(ad, diziUrl, posterLink)?.let { veriler.add(it) }


        }
        return veriler
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val mainReq = app.get(url)
        val document = mainReq.document
        val phpCookie = mainReq.cookies["PHPSESSID"].toString()
        val cntCookie = "vakTR"
        if (url.contains("/dizi/")) {
            val title = document.selectFirst("div.poster.hidden h2")?.text() ?: return null
            val poster = fixUrlNull(document.selectFirst("img.object-cover")?.attr("src"))
            val year =
                document.select("div.w-fit.min-w-fit")[1].selectFirst("span.text-sm.opacity-60")?.text()
                    ?.split(" ")?.last()?.toIntOrNull()
            val description = document.selectFirst("div.mt-2.text-sm")?.text()?.trim()
            val tags = document.selectFirst("div.poster.hidden h3")?.text()?.split(",")?.map { it }
            val rating =
                document.selectFirst("div.flex.items-center")?.selectFirst("span.text-white.text-sm")
                    ?.text()?.trim().toRatingInt()
            val actors = document.select("div.global-box h5").map {
                Actor(it.text())
            }

            val episodeses = mutableListOf<Episode>()

            for (sezon in document.select("div.hideComments.p-4 a")) {
                val sezonhref = sezon.attr("href")
                val sezonReq = app.get(sezonhref)
                val eps = sezonhref.lastOrNull()?.digitToInt()
                val sezonDoc = sezonReq.document
                val episodes = sezonDoc.select("div.episodes")
                for (bolum in episodes.select("div.cursor-pointer")) {
                    val epName = bolum.select("a").last()?.text() ?: continue
                    println("epName: $epName")
                    val epHref = fixUrlNull(bolum.select("a").last()?.attr("href")) ?: continue
                    println("epHref: $epHref")
                    val epEpisode = bolum.selectFirst("a")?.text()?.trim()?.toIntOrNull()
                    println("epEpisode: $epEpisode")
                    //val epSeason  = bolum.selectFirst("div.seasons-menu")?.text()?.substringBefore(".Sezon")?.trim()?.toIntOrNull()
                    val epSeason = eps
                    println("epSeason: $epSeason")

                    episodeses.add(
                        Episode(
                            data = epHref,
                            name = epName,
                            season = epSeason,
                            episode = epEpisode
                        )
                    )
                }
            }
            println("Episodes : " + episodeses.size)

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeses) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                addActors(actors)
            }
        } else {
            var yil: Int? = null;
            val title = document.selectFirst("div.poster.hidden h2")?.text() ?: return null
            val poster = fixUrlNull(document.selectFirst("div.flex.items-start.gap-4.slider-top img")?.attr("src"))
            val description = document.selectFirst("div.mt-2.text-md")?.text()?.trim()
            document.select("div.flex.items-center").forEach { item ->
                if (item.selectFirst("span")?.text()?.contains("tarih") == true) {
                   yil  = item.selectFirst("div.w-fit.rounded-lg")?.text()?.toIntOrNull()
                }
            }
            val tags = document.select("div.text-white.text-md.opacity-90.flex.items-center.gap-2.overflow-auto.mt-1 a").map { it.text() }
            val rating =
                document.selectFirst("div.flex.items-center")?.selectFirst("span.text-white.text-sm")
                    ?.text()?.trim().toRatingInt()
            val actors = mutableListOf<Actor>()
            document.select("div.w-fit.min-w-fit.rounded-lg") .forEach { a ->
                if (a.selectFirst("span")?.text()?.contains("Aktör") == true) {
                    actors.add(Actor(a.selectFirst("a")?.text()!!))
                }
            }
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl       = poster
                this.year            = yil
                this.plot            = description
                this.rating          = rating
                this.tags            = tags
                addActors(actors)
            }
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("RKD", "data » ${data}")
        val document = app.get(data).document
        var iframe =
            fixUrlNull(document.selectFirst("div.bg-prm iframe")?.attr("src")) ?: return false
        Log.d("RKD", "iframe » ${iframe}")

        loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        return true
    }
}
