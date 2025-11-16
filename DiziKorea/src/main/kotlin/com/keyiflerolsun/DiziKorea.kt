// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

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

class DiziKorea : MainAPI() {
    override var mainUrl              = "https://dizikorea1.com"
    override var name                 = "DiziKorea"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.AsianDrama)

        override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/-cloudstream/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
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

            if (doc.html().contains("Just a moment")) {
                return cloudflareKiller.intercept(chain)
            }

            return response
        }
    }

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-kore-dizileri/"   to "Kore Dizileri",
        "${mainUrl}/kore-filmleri-izle1/" to "Kore Filmleri",
        "${mainUrl}/tayland-dizileri/"    to "Tayland Dizileri",
        "${mainUrl}/tayland-filmleri/"    to "Tayland Filmleri",
        "${mainUrl}/cin-dizileri/"        to "Çin Dizileri",
        "${mainUrl}/cin-filmleri/"        to "Çin Filmleri"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}", interceptor = interceptor).document
        Log.d("DZK", "Ana sayfa HTML içeriği:\n${document.outerHtml()}")
        val home     = document.select("div.poster-long").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("h2")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.poster-long-image img.lazy")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    private fun Element.toPostSearchResult(): SearchResponse? {
        val title     = this.selectFirst("span")?.text()?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.poster-long-image img.lazy")?.attr("data-src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.post(
            "${mainUrl}/search",
            headers = mapOf("X-Requested-With" to "XMLHttpRequest"),
            referer = "${mainUrl}/",
            data    = mapOf("query" to query)
        ).parsedSafe<KoreaSearch>()!!.theme

        val document = Jsoup.parse(response)
        val results  = mutableListOf<SearchResponse>()

        document.select("ul li").forEach { listItem ->
            val href = listItem.selectFirst("a")?.attr("href")
            if (href != null && (href.contains("/dizi/") || href.contains("/film/"))) {
                val result = listItem.toPostSearchResult()
                result?.let { results.add(it) }
            }
        }

        return results
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url, interceptor = interceptor).document

        val title       = document.selectFirst("h1 a")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content")) ?: return null
        val year        = document.selectFirst("h1 span")?.text()?.substringAfter("(")?.substringBefore(")")?.toIntOrNull()
        val description = document.selectFirst("div.series-profile-summary p")?.text()?.trim()
        val tags        = document.select("div.series-profile-type a").mapNotNull { it.text().trim() }
        val duration    = document.selectXpath("//span[text()='Süre']//following-sibling::p").text().trim().split(" ").first().toIntOrNull()
        val trailerId     = document.selectFirst("div.series-profile-trailer")?.attr("data-yt")
        val trailerUrl = trailerId?.takeIf { it.isNotEmpty() }?.let { "https://www.youtube.com/watch?v=$it" }
        val actors      = document.select("div.series-profile-cast li").map {
            Actor(it.selectFirst("h5")!!.text(), it.selectFirst("img")!!.attr("data-src"))
        }

        if (url.contains("/dizi/")) {
            val episodes    = mutableListOf<Episode>()
            document.select("div.series-profile-episode-list").forEach {
                val epSeason = it.parent()!!.id().split("-").last().toIntOrNull()

                it.select("li").forEach ep@ { episodeElement ->
                    val epHref    = fixUrlNull(episodeElement.selectFirst("h6 a")?.attr("href")) ?: return@ep
                    val epEpisode = episodeElement.selectFirst("a.truncate data")?.text()?.trim()?.toIntOrNull()

                    episodes.add(newEpisode(epHref) {
                        this.name = "${epSeason}. Sezon ${epEpisode}. Bölüm"
                        this.season = epSeason
                        this.episode = epEpisode
                    })
                }
            }

            return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
                this.posterUrl = poster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.duration  = duration
                addActors(actors)
                addTrailer(trailerUrl)
            }
        } else {
            return newMovieLoadResponse(title, url, TvType.AsianDrama, url) {
                this.posterUrl = poster
                this.year      = year
                this.plot      = description
                this.tags      = tags
                this.duration  = duration
                addActors(actors)
                addTrailer(trailerUrl)
            }
        }
    }

    override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    Log.d("DZK", "data » $data")
    val document = app.get(data, interceptor = interceptor).document

    document.select("div.video-services button").forEach {
        val rawHhs = it.attr("data-hhs")
        Log.d("DZK", "Found button with data-hhs: $rawHhs")

        val iframe = fixUrlNull(rawHhs) ?: return@forEach
        Log.d("DZK", "iframe » $iframe")

        loadExtractor(iframe, "$mainUrl/", subtitleCallback, callback)
    }

    return true
    }
}
