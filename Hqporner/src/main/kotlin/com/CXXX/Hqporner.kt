package com.CXXX


import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class Hqporner : MainAPI() {
    override var mainUrl              = "https://m.hqporner.com"
    override var name                 = "Hqporner"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "${mainUrl}/category/milf" to "Milf",
        "${mainUrl}/category/asian" to "Asian",
        "${mainUrl}/category/japanese-girls-porn" to "Japanese",
        "${mainUrl}/studio/free-brazzers-videos" to "Brazzers",
        "${mainUrl}/category/big-tits" to "Big Tits",
        "${mainUrl}/category/1080p-porn" to "1080p Porn",
        "${mainUrl}/category/4k-porn" to "4k Porn",
        "${mainUrl}/top/week" to "Week TOP",
        "${mainUrl}/top/month" to "Month TOP",
        "${mainUrl}/top" to "All Time Best Porn",


    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div.box.page-content div.row section").mapNotNull {
            it.toSearchResult()
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val capitalizedTitle     = this.selectFirst("h3 a")?.text() ?:"No Title"
        val title  = capitalizedTitle.split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        val href       = fixUrl(this.selectFirst("h3 a")!!.attr("href"))
        val posterUrl  = fixUrlNull(this.select("img").attr("src"))

        return newMovieSearchResponse(title, LoadUrl(href, posterUrl).toJson(),TvType.NSFW) {
            this.posterUrl = posterUrl
        }

    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..2) {
            val document = app.get("${mainUrl}/?q=$query&p=$i").document
            val results = document.select("div.box.page-content div.row section").mapNotNull { it.toSearchResult() }
            searchResponse.addAll(results)
            if (results.isEmpty()) break
        }
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse? {
        val d = tryParseJson<LoadUrl>(url) ?: return null
        val document = app.get(d.href).document
        val capitalizedTitle= document.selectFirst("header > h1")?.text()?.trim().toString()
        val title  = capitalizedTitle.split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        val poster= d.posterUrl
        val plot="Hqporner"
        return newMovieLoadResponse(title, url, TvType.NSFW, d.href) {
            this.posterUrl       = poster
            this.plot=plot
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val doc=document.toString()
        val rawurl = Regex("""url: '/blocks/altplayer\.php\?i=//(.*?)',""").find(doc)?.groupValues?.get(1) ?:""
        val href= "https://$rawurl"
        loadExtractor(
            href,
            subtitleCallback,
            callback
        )
        return true
    }

    data class LoadUrl(
        val href: String,
        val posterUrl: String?
    )
}
