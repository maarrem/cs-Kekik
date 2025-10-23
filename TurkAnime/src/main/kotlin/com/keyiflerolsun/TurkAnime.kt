// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import org.jsoup.nodes.Document
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import android.util.Base64
import com.lagradost.cloudstream3.extractors.helper.AesHelper

class TurkAnime : MainAPI() {
    override var mainUrl              = "https://www.turkanime.tv"
    override var name                 = "TurkAnime"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = false
    override val supportedTypes       = setOf(TvType.Anime)

    override val mainPage = mainPageOf(
        "${mainUrl}/anime-turu/1/Aksiyon"                                   to "Aksiyon",
        "${mainUrl}/anime-turu/3/Arabalar"                                  to "Arabalar",
        "${mainUrl}/anime-turu/38/Askeri"                                   to "Askeri",
        "${mainUrl}/anime-turu/5/Avangard"                                  to "Avangard",
        "${mainUrl}/anime-turu/24/Bilim_Kurgu"                              to "Bilim Kurgu",
        "${mainUrl}/anime-turu/16/B%C3%BCy%C3%BC"                           to "Büyü",
        "${mainUrl}/anime-turu/15/%C3%87ocuklar"                            to "Çocuklar",
        "${mainUrl}/anime-turu/37/Do%C4%9Fa%C3%BCst%C3%BC_G%C3%BC%C3%A7ler" to "Doğaüstü Güçler",
        "${mainUrl}/anime-turu/17/D%C3%B6v%C3%BC%C5%9F_Sanatlar%C4%B1"      to "Dövüş Sanatları",
        "${mainUrl}/anime-turu/8/Dram"                                      to "Dram",
        "${mainUrl}/anime-turu/9/Ecchi"                                     to "Ecchi",
        "${mainUrl}/anime-turu/10/Fantastik"                                to "Fantastik",
        "${mainUrl}/anime-turu/41/Gerilim"                                  to "Gerilim",
        "${mainUrl}/anime-turu/7/Gizem"                                     to "Gizem",
        "${mainUrl}/anime-turu/35/Harem"                                    to "Harem",
        "${mainUrl}/anime-turu/43/Josei"                                    to "Josei",
        "${mainUrl}/anime-turu/4/Komedi"                                    to "Komedi",
        "${mainUrl}/anime-turu/14/Korku"                                    to "Korku",
        "${mainUrl}/anime-turu/2/Macera"                                    to "Macera",
        "${mainUrl}/anime-turu/18/Mecha"                                    to "Mecha",
        "${mainUrl}/anime-turu/19/M%C3%BCzik"                               to "Müzik",
        "${mainUrl}/anime-turu/23/Okul"                                     to "Okul",
        "${mainUrl}/anime-turu/11/Oyun"                                     to "Oyun",
        "${mainUrl}/anime-turu/20/Parodi"                                   to "Parodi",
        "${mainUrl}/anime-turu/39/Polisiye"                                 to "Polisiye",
        "${mainUrl}/anime-turu/40/Psikolojik"                               to "Psikolojik",
        "${mainUrl}/anime-turu/22/Romantizm"                                to "Romantizm",
        "${mainUrl}/anime-turu/21/Samuray"                                  to "Samuray",
        "${mainUrl}/anime-turu/42/Seinen"                                   to "Seinen",
        "${mainUrl}/anime-turu/6/%C5%9Eeytanlar"                            to "Şeytanlar",
        "${mainUrl}/anime-turu/25/Shoujo"                                   to "Shoujo",
        "${mainUrl}/anime-turu/26/Shoujo_Ai"                                to "Shoujo Ai",
        "${mainUrl}/anime-turu/27/Shounen"                                  to "Shounen",
        "${mainUrl}/anime-turu/28/Shounen_Ai"                               to "Shounen Ai",
        "${mainUrl}/anime-turu/30/Spor"                                     to "Spor",
        "${mainUrl}/anime-turu/31/S%C3%BCper_G%C3%BC%C3%A7ler"              to "Süper Güçler",
        "${mainUrl}/anime-turu/13/Tarihi"                                   to "Tarihi",
        "${mainUrl}/anime-turu/29/Uzay"                                     to "Uzay",
        "${mainUrl}/anime-turu/32/Vampir"                                   to "Vampir",
        "${mainUrl}/anime-turu/33/Yaoi"                                     to "Yaoi",
        "${mainUrl}/anime-turu/36/Ya%C5%9Famdan_Kesitler"                   to "Yaşamdan Kesitler",
        "${mainUrl}/anime-turu/34/Yuri"                                     to "Yuri",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div#orta-icerik div.panel").mapNotNull { it.toMainPageResult() }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.toMainPageResult(): SearchResponse? {
        val title     = this.selectFirst("div.panel-title a")?.text() ?: return null
        val href      = fixUrlNull(this.selectFirst("div.panel-title a")?.attr("href")) ?: return null
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))

        return newAnimeSearchResponse(title, href, TvType.Anime) { this.posterUrl = posterUrl }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.post("${mainUrl}/arama", data=mapOf("arama" to query)).document

        return document.select("div#orta-icerik div.panel").mapNotNull { it.toMainPageResult() }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("div#detayPaylas div.panel-title")?.text()?.trim() ?: return null
        val poster      = fixUrlNull(document.selectFirst("div#detayPaylas div.imaj img")?.attr("data-src"))
        val description = document.selectFirst("div#detayPaylas p.ozet")?.text()?.trim()
        val year        = document.selectFirst("div#detayPaylas a[href*='yil/']")?.attr("href")?.substringAfter("yil/")?.toIntOrNull()
        val tags        = document.select("div#animedetay a[href*='anime-turu']").map { it.text() }
        val rating      = document.selectFirst("span.puan")?.text()?.trim()?.toRatingInt()

        val bolumlerUrl = fixUrlNull(document.selectFirst("a[data-url*='ajax/bolumler&animeId=']")?.attr("data-url")) ?: return null
        val bolumlerDoc = app.get(
            bolumlerUrl,
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "token"            to document.selectFirst("meta[name='_token']")!!.attr("content")
            ),
            cookies = mapOf("yasOnay" to "1")
        ).document

        val episodes = bolumlerDoc.select("div#bolum-list li").mapNotNull {
            val epHref    = fixUrlNull(it.selectFirst("a[href*='/video/']")?.attr("href")) ?: return@mapNotNull null
            val epName    = it.selectFirst("span.bolumAdi")?.text()?.trim() ?: return@mapNotNull null
            val epSeason  = 1
            val epTitle   = it.selectFirst("a[href*='/video/']")?.attr("title")?.trim() ?: return@mapNotNull null
            val epEpisode = Regex("""(\d+). Bölüm""").find(epTitle)?.groupValues?.get(1)?.toIntOrNull() ?: 1

            newEpisode(epHref) {
                this.name = epName
                this.season = epSeason
                this.episode = epEpisode
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
            this.posterUrl = poster
            this.plot      = description
            this.year      = year
            this.tags      = tags
            this.rating    = rating
        }
    }

    private fun iframe2AesLink(iframe: String): String? {
        var aesData = iframe.substringAfter("embed/#/url/").substringBefore("?status")
        aesData     = String(Base64.decode(aesData, Base64.DEFAULT))

        val aesKey  = "710^8A@3@>T2}#zN5xK?kR7KNKb@-A!LzYL5~M1qU0UfdWsZoBm4UUat%}ueUv6E--*hDPPbH7K2bp9^3o41hw,khL:}Kx8080@M"
        val aesLink = AesHelper.cryptoAESHandler(aesData, aesKey.toByteArray(), false)?.replace("\\", "") ?: throw ErrorLoadingException("failed to decrypt")

        return fixUrlNull(aesLink.replace("\"", ""))
    }

    private suspend fun iframe2Load(document: Document, @Suppress("UNUSED_PARAMETER") iframe: String, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        // val mainVideo = iframe2AesLink(iframe)
        // if (mainVideo != null) {
        //     val mainKey = mainVideo.split("/").last()
        //     val mainAPI = app.get(
        //         "${mainUrl}/sources/${mainKey}/true",
        //         headers = mapOf(
        //             "Content-Type"     to "application/json",
        //             "X-Requested-With" to "XMLHttpRequest",
        //             "Csrf-Token"       to "EqdGHqwZJvydjfbmuYsZeGvBxDxnQXeARRqUNbhRYnPEWqdDnYFEKVBaUPCAGTZA",
        //             "Connection"       to "keep-alive",
        //             "Sec-Fetch-Dest"   to "empty",
        //             "Sec-Fetch-Mode"   to "cors",
        //             "Sec-Fetch-Site"   to "same-origin",
        //             "Pragma"           to "no-cache",
        //             "Cache-Control"    to "no-cache",
        //         ),
        //         referer = mainVideo,
        //         cookies = mapOf("yasOnay" to "1")
        //     ).text

        //     val m3uLink = fixUrlNull(Regex("""file\":\"([^\"]+)""").find(mainAPI)?.groupValues?.get(1)?.replace("\\", ""))
        //     Log.d("TRANM", "m3uLink » ${m3uLink}")

        //     if (m3uLink != null) {
        //         callback.invoke(
        //             ExtractorLink(
        //                 source  = this.name,
        //                 name    = this.name,
        //                 url     = m3uLink,
        //                 referer = "${mainVideo}",
        //                 quality = Qualities.Unknown.value,
        //                 isM3u8  = true,
        //             )
        //         )
        //     }
        // }

        for (button in document.select("button[onclick*='ajax/videosec']")) {
            val butonLink = fixUrlNull(button.attr("onclick").substringAfter("IndexIcerik('").substringBefore("'")) ?: continue
            val butonName = button.ownText().trim()
            val subDoc    = app.get(butonLink, headers=mapOf("X-Requested-With" to "XMLHttpRequest")).document

            val subFrame  = fixUrlNull(subDoc.selectFirst("iframe")?.attr("src")) ?: continue
            val subLink   = iframe2AesLink(subFrame) ?: continue
            Log.d("TRANM", "$butonName » $subLink")

            loadExtractor(subLink, "${mainUrl}/", subtitleCallback, callback)
        }
    }
override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
    Log.d("TRANM", "data » $data")
    val document = app.get(data).document

    val iframeElement = document.selectFirst("iframe")
    val iframe = fixUrlNull(iframeElement?.attr("src"))

    if (iframe == null || iframe.contains("a-ads.com")) {
        val buttons = document.select("button[onclick*='IndexIcerik']")

        for (button in buttons) {
            val onclickAttr = button.attr("onclick")
            val subLink = onclickAttr.substringAfter("IndexIcerik('").substringBefore("'")
                .takeIf { it.isNotBlank() }
                ?.let { fixUrlNull(it) } ?: continue

            Log.d("TRANM", "Extra seçici ile alınan link: $subLink")

            val subResponse = app.get(subLink, headers = mapOf("X-Requested-With" to "XMLHttpRequest"))
            val subHtml = subResponse.body?.string().orEmpty()

            val subDoc = org.jsoup.Jsoup.parse(subHtml, subLink)

            // Önce artplayer-app içindeki data-url kontrol edilir
            val dataUrl = subDoc.selectFirst("div.artplayer-app")?.attr("data-url")
            if (dataUrl != null && dataUrl.endsWith(".m3u8")) {
                Log.d("TRANM", "M3U8 data-url bulundu: $dataUrl")
                callback(
                    newExtractorLink(
                        name = "TurkAnime",
                        source = "TurkAnime",
                        url = dataUrl,
                        type = ExtractorLinkType.M3U8
                        ) {
                        quality = Qualities.Unknown.value
                        headers = mapOf("Referer" to subLink)
            }
                )
                continue
            }

            // Eğer data-url yoksa iframe'e fallback yap
            val subFrame = fixUrlNull(subDoc.selectFirst("iframe")?.attr("src")) ?: continue
            Log.d("TRANM", "subFrame » $subFrame")

            iframe2Load(subDoc, subFrame, subtitleCallback, callback)
        }
    } else {
        iframe2Load(document, iframe, subtitleCallback, callback)
    }

    return true
}
}
