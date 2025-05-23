// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer

class DiziYou : MainAPI() {
    override var mainUrl              = "https://www.diziyou7.com"
    override var name                 = "DiziYou"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.TvSeries)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        if (page > 1) return newHomePageResponse(request.name, emptyList())
    
        val document = app.get(mainUrl).document
        val home = ArrayList<HomePageList>()
    
        // 1. Popüler Dizilerden Son Bölümler
        val populer = document.select("div.dsmobil div.listepisodes").mapNotNull { el ->
            val episodeAnchor = el.selectFirst("a") ?: return@mapNotNull null
            val fullEpisodeUrl = fixUrlNull(episodeAnchor.attr("href")) ?: return@mapNotNull null
            val slug = fullEpisodeUrl
                .removePrefix("$mainUrl/")
                .replace(Regex("""-\d+-sezon-\d+-bolum/?$"""), "")
            val href = "$mainUrl/$slug/"
        
            // alt="..." değeri başlık olarak
            val title = episodeAnchor.selectFirst("img[alt]")?.attr("alt")?.trim()
                ?: return@mapNotNull null
        
            // poster görseli (data-src veya src)
            val poster = fixUrlNull(
                episodeAnchor.selectFirst("img.lazy")?.attr("data-src")
                    ?: episodeAnchor.selectFirst("img")?.attr("src")
            )
        
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (populer.isNotEmpty()) home.add(HomePageList("Popüler Dizilerden Son Bölümler", populer))
    
        // 2. Son Eklenen Diziler
        val sonEklenen = document.select("div.dsmobil2 div#list-series-main").mapNotNull { el ->
            val href = fixUrlNull(el.selectFirst("div.cat-img-main a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(el.selectFirst("div.cat-img-main img")?.attr("src"))
            val title = el.selectFirst("div.cat-title-main a")?.text()?.trim() ?: return@mapNotNull null
    
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (sonEklenen.isNotEmpty()) home.add(HomePageList("Son Eklenen Diziler", sonEklenen))
    
        // 3. Efsane Diziler
        val efsane = document.select("div.incontent div#list-series-main").mapNotNull { el ->
            val href = fixUrlNull(el.selectFirst("div.cat-img-main a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(el.selectFirst("div.cat-img-main img")?.attr("src"))
            val title = el.selectFirst("div.cat-title-main a")?.text()?.trim() ?: return@mapNotNull null
    
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (efsane.isNotEmpty()) home.add(HomePageList("Efsane Diziler", efsane))
    
        // 4. Dikkat Çeken Diziler
        val dikkat = document.select("div.incontentyeni div#list-series-main").mapNotNull { el ->
            val href = fixUrlNull(el.selectFirst("div.cat-img-main a")?.attr("href")) ?: return@mapNotNull null
            val poster = fixUrlNull(el.selectFirst("div.cat-img-main img")?.attr("src"))
            val title = el.selectFirst("div.cat-title-main a")?.text()?.trim() ?: return@mapNotNull null
    
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                posterUrl = poster
            }
        }
        if (dikkat.isNotEmpty()) home.add(HomePageList("Dikkat Çeken Diziler", dikkat))
    
        return newHomePageResponse(home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div#categorytitle a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div#categorytitle a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("${mainUrl}/?s=${query}").document

        return document.select("div.incontent div#list-series").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title           = document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster          = fixUrlNull(document.selectFirst("div.category_image img")?.attr("src"))
        val description     = document.selectFirst("div.diziyou_desc")?.ownText()?.trim()
        val year            = document.selectFirst("span.dizimeta:contains(Yapım Yılı)")?.nextSibling()?.toString()?.trim()?.toIntOrNull()
        val tags            = document.select("div.genres a").map { it.text() }
        val rating          = document.selectFirst("span.dizimeta:contains(IMDB)")?.nextSibling()?.toString()?.trim()?.toRatingInt()
        val actors          = document.selectFirst("span.dizimeta:contains(Oyuncular)")?.nextSibling()?.toString()?.trim()?.split(", ")?.map { Actor(it) }
        val trailer         = document.selectFirst("iframe.trailer-video")?.attr("src")

        val episodes = document.select("div.bolumust").mapNotNull {
            val epName    = it.selectFirst("div.baslik")?.ownText()?.trim() ?: return@mapNotNull null
            val epHref    = it.closest("a")?.attr("href")?.let { href -> fixUrlNull(href) } ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+)\. Bölüm""").find(epName)?.groupValues?.get(1)?.toIntOrNull()
            val epSeason  = Regex("""(\d+)\. Sezon""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name = it.selectFirst("div.bolumismi")?.text()?.trim()?.replace(Regex("""[()]"""), "")?.trim() ?: epName
                this.season = epSeason
                this.episode = epEpisode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.rating    = rating
            addActors(actors)
            addTrailer(trailer)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("DZY", "data » $data")
        val document = app.get(data).document

        val itemId     = document.selectFirst("iframe#diziyouPlayer")?.attr("src")?.split("/")?.lastOrNull()?.substringBefore(".html") ?: return false
        Log.d("DZY", "itemId » $itemId")

        val subTitles  = mutableListOf<DiziyouSubtitle>()
        val streamUrls = mutableListOf<DiziyouStream>()
        val storage    = mainUrl.replace("www", "storage")

        document.select("span.diziyouOption").forEach {
            val optId   = it.attr("id")

            if (optId == "turkceAltyazili") {
                subTitles.add(DiziyouSubtitle("Turkish", "${storage}/subtitles/${itemId}/tr.vtt"))
                streamUrls.add(DiziyouStream("Orjinal Dil", "${storage}/episodes/${itemId}/play.m3u8"))
            }

            if (optId == "ingilizceAltyazili") {
                subTitles.add(DiziyouSubtitle("English", "${storage}/subtitles/${itemId}/en.vtt"))
                streamUrls.add(DiziyouStream("Orjinal Dil", "${storage}/episodes/${itemId}/play.m3u8"))
            }

            if (optId == "turkceDublaj") {
                streamUrls.add(DiziyouStream("Türkçe Dublaj", "${storage}/episodes/${itemId}_tr/play.m3u8"))
            }
        }

        for (sub in subTitles) {
            subtitleCallback.invoke(
                SubtitleFile(
                    lang = sub.name,
                    url  = fixUrl(sub.url)
                )
            )
        }

        for (stream in streamUrls) {
            callback.invoke(
             newExtractorLink(
                source = this.name,
                name = this.name,
                url = stream.url,
                type    = INFER_TYPE
            ) {
                headers = mapOf("Referer" to "${mainUrl}/")
                quality = Qualities.Unknown.value
            }
            )
        }

        return true
    }

    data class DiziyouSubtitle(val name: String, val url: String)
    data class DiziyouStream(val name: String, val url: String)
}
