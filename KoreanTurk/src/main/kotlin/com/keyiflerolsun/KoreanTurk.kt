// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlin.random.Random

class KoreanTurk : MainAPI() {
    override var mainUrl              = "https://www.koreanturk.net"
    override var name                 = "KoreanTurk"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.AsianDrama)

    override val mainPage = mainPageOf(
        "${mainUrl}/bolumler/page/"       to "Son Eklenenler",
        "${mainUrl}/Konu-Aile"            to "Aile",
        "${mainUrl}/Konu-Aksiyon"         to "Aksiyon",
        "${mainUrl}/Konu-Bilim-Kurgu"     to "Bilim Kurgu",
        "${mainUrl}/Konu-Donem"           to "Dönem",
        "${mainUrl}/Konu-Dram"            to "Dram",
        "${mainUrl}/Konu-Fantastik"       to "Fantastik",
        "${mainUrl}/Konu-Genclik"         to "Gençlik",
        "${mainUrl}/Konu-Gerilim"         to "Gerilim",
        "${mainUrl}/Konu-Gizem"           to "Gizem",
        "${mainUrl}/Konu-Hukuk"           to "Hukuk",
        "${mainUrl}/Konu-Komedi"          to "Komedi",
        "${mainUrl}/Konu-Korku"           to "Korku",
        "${mainUrl}/Konu-Medikal"         to "Medikal",
        "${mainUrl}/Konu-Mini-Dizi"       to "Mini Dizi",
        "${mainUrl}/Konu-Okul"            to "Okul",
        "${mainUrl}/Konu-Polisiye-Askeri" to "Polisiye-Askeri",
        "${mainUrl}/Konu-Romantik"        to "Romantik",
        "${mainUrl}/Konu-Romantik-Komedi" to "Romantik Komedi",
        "${mainUrl}/Konu-Suc"             to "Suç",
        "${mainUrl}/Konu-Tarih"           to "Tarih",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (request.data.contains("Konu-")) {
            val document = app.get(request.data).document
            val home     = document.selectXpath("//img[contains(@onload, 'NcodeImageResizer')]")
                .shuffled(Random(System.nanoTime()))
                .take(12)
                .mapNotNull { it.toKonuResult() }

            return newHomePageResponse(
                list = HomePageList(
                    name = request.name,
                    list = home,
                    isHorizontalImages = false
                ),
                hasNext = false
            )
        } else {
            val document = app.get("${request.data}${page}").document
            val home     = document.select("div.standartbox").mapNotNull { it.toSearchResult() }

            return newHomePageResponse(request.name, home)
        }
    }

    private fun removeEpisodePart(url: String): String {
        val regex = "-[0-9]+(-final)?-bolum-izle\\.html".toRegex()
        return regex.replace(url, "")
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val dizi      = this.selectFirst("h2 span")?.text()?.trim() ?: return null
        val bolum     = this.selectFirst("h2")?.ownText()?.substringBefore(".Bölüm")?.trim()
        val title     = "$dizi | $bolum"

        var href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        if (href.contains("izle.html")) {
            href = removeEpisodePart(href)
        }

        val posterUrl = fixUrlNull(this.selectFirst("div.resimcik img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    private fun Element.toKonuResult(): SearchResponse? {
        val title     = this.selectXpath("preceding-sibling::a[1]").text().trim()
        val href      = fixUrlNull(this.selectXpath("preceding-sibling::a[1]").attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.AsianDrama) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/").document

        val searchResults = document.select(".cat-item").mapNotNull {
            val title = it.text()
            val href  = it.firstElementChild()?.attr("href")

            if (title.contains(query, ignoreCase = true) && href != null) {
                // ! i don't want to put posterUrl because already their website slow and getting every page is time consuming
                // * val diziPage  = app.get(href).document
                // * val posterUrl = diziPage.selectFirst("div.resimcik img")?.attr("src")?.removeSuffix("-60x60.jpg") + ".jpg" //Assuming every image has this res, might change in the future
                newTvSeriesSearchResponse(title, href) {
                    this.posterUrl = ""
                }
            } else {
                null
            }
        }

        return searchResults
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("h3")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div.resimcik img")?.attr("src"))
        val description = document.selectFirst("[property='og:description']")?.attr("content")?.trim()

        val episodes    = document.select("div.standartbox a").mapNotNull {
            val epName    = it.selectFirst("h2")?.ownText()?.trim() ?: return@mapNotNull null
            val epHref    = fixUrlNull(it.attr("href")) ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+)\.Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            val epSeason  = Regex("""(\d+)\.Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name    = epName
                this.season  = epSeason
                this.episode = epEpisode
            }
        }


        return newTvSeriesLoadResponse(title, url, TvType.AsianDrama, episodes) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("KRT", "data » $data")
        val document = app.get(data).document

        val iframes = mutableListOf<String>()

        document.select("div.filmcik div.tab-pane iframe").forEach {
            val iframe = fixUrlNull(it.attr("src")) ?: return@forEach
            iframes.add(iframe)
        }

        document.select("div.filmcik div.tab-pane a").forEach {
            val iframe = fixUrlNull(it.attr("href")) ?: return@forEach
            iframes.add(iframe)
        }

        iframes.forEach { iframe ->
            Log.d("KRT", "iframe » $iframe")
            loadExtractor(iframe, "${mainUrl}/", subtitleCallback, callback)
        }

        return true
    }
}
