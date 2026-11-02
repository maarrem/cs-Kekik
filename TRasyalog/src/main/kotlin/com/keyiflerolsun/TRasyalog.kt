package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.json.*
import org.jsoup.Jsoup


class TRasyalog : MainAPI() {
    override var mainUrl        = "https://asyalog.co"
    override var name           = "AsyaLog"
    override val hasMainPage    = true
    override var lang           = "tr"
    override val hasQuickSearch = false
    override val supportedTypes = setOf(TvType.TvSeries)

    override var sequentialMainPage = true        // * https://recloudstream.github.io/dokka/library/com.lagradost.cloudstream3/-main-a-p-i/index.html#-2049735995%2FProperties%2F101969414
    override var sequentialMainPageDelay       = 500L  // ? 0.5 saniye
    override var sequentialMainPageScrollDelay = 500L  // ? 0.5 saniye

    override val mainPage = mainPageOf(
        "${mainUrl}/category/kore-dizileri-izle-guncel" to "Kore Dizileri",
        "${mainUrl}/category/cin-dizileri"              to "Çin Dizileri",
        "${mainUrl}/category/tayland-dizileri"          to "TaylandDizileri",
        "${mainUrl}/category/japon-dizileri"            to "Japon Diziler",
        "${mainUrl}/category/endonezya-dizileri"        to "Endonezya Diziler",
        "${mainUrl}/category/devam-eden-diziler"        to "Devam eden Diziler"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("${request.data}/page/$page/").document
        val home     = document.select("div#archiveListing div.post-container").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("a img")?.attr("alt")?.trim() ?: return null
        val href      = fixUrlNull(this.selectFirst("a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("div.thumbnail img")?.let { img ->
        img.attr("data-src").takeIf { it.isNotEmpty() } ?: img.attr("src")
    }
)

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.post-container").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
    
        val title = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = fixUrlNull(
            document.selectFirst("img.wp-image-66892")?.attr("data-src")
                ?: document.selectFirst("img.wp-image-66892")?.attr("src")
        )
        val description = document.selectFirst("h2 + p")?.text()?.trim()
        val tags = document.selectFirst("b.inline:contains(Tür:)")?.parent()?.text()?.substringAfter("Tür:")?.trim()?.split(", ")?: emptyList()
    
        val episodeList = mutableListOf<Episode>()
        val addedEpisodeNumbers = mutableSetOf<Int>()
    
        val dataUrls = document.select("span[data-url]").mapNotNull {
            it.attr("data-url")?.trim()?.takeIf { it.isNotEmpty() }?.let { fixUrl(it) }
        }
    
        // Ayır: toplu (1-5, 6-10) ve tekli (1, 2...) bölümler
        val groupedPartUrls = dataUrls.filter { Regex("""\d+-\d+""").containsMatchIn(it) }
        val singlePartUrls = dataUrls.filterNot { it in groupedPartUrls }
    
        // 🧩 Toplu bölümler
        for (partUrl in groupedPartUrls) {
            val partDoc = app.get(partUrl).document
            val tabContents = partDoc.select("div[id^=tab-][id*=bolum]")
            for (tab in tabContents) {
                val tabId = tab.id()
                val isFinal = tabId.contains("final", ignoreCase = true)
                val episodeNumber = Regex("""-(\d+)-bolum""").find(tabId)?.groupValues?.get(1)?.toIntOrNull()
                if ((episodeNumber != null && episodeNumber !in addedEpisodeNumbers) || isFinal) {
                    val iframe = tab.selectFirst("iframe")
                    val iframeUrl = iframe?.attr("data-src")?.ifBlank { iframe.attr("src") }?.let {
                        if (it.startsWith("http")) it else "https:$it"
                    } ?: continue
    
                    episodeList.add(newEpisode(iframeUrl) {
                        name = if (isFinal) "Final Bölüm" else "$episodeNumber. Bölüm"
                        episode = episodeNumber
                    })
                    episodeNumber?.let { addedEpisodeNumbers.add(it) }
                }
            }
        }
    
        // 🧩 Tekli bölümler
        for (epUrl in singlePartUrls) {
            val epDoc = app.get(epUrl).document
            val iframe = epDoc.selectFirst("iframe")
            val iframeUrl = iframe?.attr("data-src")?.ifBlank { iframe.attr("src") }?.let {
                if (it.startsWith("http")) it else "https:$it"
            } ?: continue
    
            val isFinal = epUrl.contains("final", ignoreCase = true)
            val episodeNumber = Regex("""-(\d+)-bolum""").find(epUrl)?.groupValues?.get(1)?.toIntOrNull()
    
            if ((episodeNumber != null && episodeNumber !in addedEpisodeNumbers) || isFinal) {
                episodeList.add(newEpisode(iframeUrl) {
                    name = if (isFinal) "Final Bölüm" else "$episodeNumber. Bölüm"
                    episode = episodeNumber
                })
                episodeNumber?.let { addedEpisodeNumbers.add(it) }
            }
        }
    
        val sortedEpisodes = episodeList.sortedBy { it.episode ?: Int.MAX_VALUE }
    
        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEpisodes) {
            this.posterUrl = poster
            this.plot = description
            this.tags = tags
        }
    }

override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    Log.d("TRASYA", "data » $data")
    // data zaten doğrudan iframe URL'si ise loadExtractor'a gönderiyoruz
    loadExtractor(data, "$mainUrl/", subtitleCallback, callback)

    return true
}
}
