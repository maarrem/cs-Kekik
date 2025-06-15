// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class SuperFilmGeldi : MainAPI() {
    override var mainUrl              = "https://www.superfilmgeldi.one"
    override var name                 = "SuperFilmGeldi"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Movie)

    override val mainPage = mainPageOf(
        "${mainUrl}/page/"                                        to "Son Eklenenler",
        "${mainUrl}/hdizle/category/aksiyon/page/"                to "Aksiyon",
        "${mainUrl}/hdizle/category/animasyon/page/"              to "Animasyon",
        "${mainUrl}/hdizle/category/belgesel/page/"               to "Belgesel",
        "${mainUrl}/hdizle/category/bilim-kurgu/page/"            to "Bilim Kurgu",
        "${mainUrl}/hdizle/category/fantastik/page/"              to "Fantastik",
        "${mainUrl}/hdizle/category/komedi-filmleri/page/"        to "Komedi Filmleri",
        "${mainUrl}/hdizle/category/macera/page/"                 to "Macera",
        "${mainUrl}/hdizle/category/gerilim/page/"                to "Gerilim",
        "${mainUrl}/hdizle/category/suc/page/"                    to "Suç",
        "${mainUrl}/hdizle/category/karete-filmleri/page/"        to "Karete Filmleri",
        "${mainUrl}/hdizle/category/yesilcam-erotik-izle/page/"   to "Yeşilçam Erotik",
        "${mainUrl}/hdizle/category/hd-erotik-filmler-izle/page/" to "Erotik Filmler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}${page}").document
        val home     = document.select("div.movie-preview-content").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun removeUnnecessarySuffixes(title: String): String {
        val unnecessarySuffixes = listOf(
            " izle", 
            " full film", 
            " filmini full",
            " full türkçe",
            " alt yazılı", 
            " altyazılı", 
            " tr dublaj",
            " hd türkçe",
            " türkçe dublaj",
            " yeşilçam ",
            " erotik fil",
            " türkçe",
            " yerli",
        )

        var cleanedTitle = title.trim()

        for (suffix in unnecessarySuffixes) {
            val regex = Regex("${Regex.escape(suffix)}.*$", RegexOption.IGNORE_CASE)
            cleanedTitle = cleanedTitle.replace(regex, "").trim()
        }

        return cleanedTitle
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title     = this.selectFirst("span.movie-title a")?.text()?.substringBefore(" izle") ?: return null
        val href      = fixUrlNull(this.selectFirst("span.movie-title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(removeUnnecessarySuffixes(title), href, TvType.Movie) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}?s=${query}").document

        return document.select("div.movie-preview-content").mapNotNull { it.toSearchResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("div.title h1")?.text()?.trim()?.substringBefore(" izle") ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.poster img")?.attr("src"))
        val year            = document.selectFirst("div.release a")?.text()?.toIntOrNull()
        val description     = document.selectFirst("div.excerpt p")?.text()?.trim()
        val tags            = document.select("div.categories a").map { it.text() }
        val rating          = document.selectFirst("span.imdb-rating")?.text()?.trim()?.split(" ")?.first()?.toRatingInt()
        val recommendations = document.select("div.film-content div.existing_item").mapNotNull { it.toSearchResult() }
        val actors          = document.select("div.actor a").map {
            Actor(it.text())
        }

        return newMovieLoadResponse(removeUnnecessarySuffixes(title), url, TvType.Movie, url) {
            this.posterUrl       = poster
            this.year            = year
            this.plot            = description
            this.tags            = tags
            this.rating          = rating
            this.recommendations = recommendations
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("SFG", "data » $data")
        val document = app.get(data).document
        val iframe   = fixUrlNull(document.selectFirst("div#vast iframe")?.attr("src")) ?: return false
        Log.d("SFG", "iframe » $iframe")

        if (iframe.contains("mix") and iframe.contains("index.php?data=")) {
            val iSource  = app.get(iframe, referer="${mainUrl}/").text
            val mixPoint = Regex("""videoUrl":"(.*)","videoServer""").find(iSource)?.groupValues?.get(1)?.replace("\\", "") ?: return false

            var endPoint = "?s=0&d="

            if (iframe.contains("mixlion")) {
                endPoint = "?s=3&d="
            } else if (iframe.contains("mixeagle")) {
                endPoint = "?s=1&d="
            }

            val m3uLink = iframe.substringBefore("/player") + mixPoint + endPoint
            Log.d("SFG", "m3uLink » $m3uLink")

            callback.invoke(
                newExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = m3uLink,
				type = ExtractorLinkType.M3U8
            ) {
                quality = Qualities.Unknown.value
                headers = mapOf("Referer" to iframe)
            }
            )
        } else {
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}