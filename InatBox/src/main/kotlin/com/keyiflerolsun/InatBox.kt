package com.keyiflerolsun

import android.util.Log
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Interceptor
import org.json.JSONArray
import java.net.URI
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject

class InatBox : MainAPI() {
    private val contentUrl  = "https://dizibox.cfd"

    override var name                 = "InatBox"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.Movie, TvType.TvSeries, TvType.Live)
    override var sequentialMainPage   = false

    private val urlToSearchResponse = mutableMapOf<String, SearchResponse>()
    private val aesKey = "ywevqtjrurkwtqgz" //Master secret and iv key

    override val mainPage = mainPageOf(
        "https://boxyz.cfd/CDN/001_STR/boxyz.cfd/spor_v2.php" to "Spor Kanalları",
        "${contentUrl}/tv/cable.php"                          to "Kanallar Liste 1",
        "${contentUrl}/tv/list2.php"                          to "Kanallar Liste 2",
        "${contentUrl}/tv/sinema.php"                         to "Sinema Kanalları",
        "${contentUrl}/tv/belgesel.php"                       to "Belgesel Kanalları",
        "${contentUrl}/tv/ulusal.php"                         to "Ulusal Kanallar",
        "${contentUrl}/tv/haber.php"                          to "Haber Kanalları",
        "${contentUrl}/tv/cocuk.php"                          to "Çocuk Kanalları",
        "${contentUrl}/tv/dini.php"                           to "Dini Kanallar",
        "${contentUrl}/ex/index.php"                          to "EXXEN",
        "${contentUrl}/ga/index.php"                          to "Gain",
        "${contentUrl}/max/index.php"                         to "Max-BluTV",
        "${contentUrl}/nf/index.php"                          to "Netflix",
        "${contentUrl}/dsny/index.php"                        to "Disney+",
        "${contentUrl}/amz/index.php"                         to "Amazon Prime",
        "${contentUrl}/hb/index.php"                          to "HBO Max",
        "${contentUrl}/tbi/index.php"                         to "Tabii",
        "${contentUrl}/film/mubi.php"                         to "Mubi",
        "${contentUrl}/ccc/index.php"                         to "TOD",
        "${contentUrl}/yabanci-dizi/index.php"                to "Yabancı Diziler",
        "${contentUrl}/yerli-dizi/index.php"                  to "Yerli Diziler",
        "${contentUrl}/film/yerli-filmler.php"                to "Yerli Filmler",
        "${contentUrl}/film/4k-film-exo.php"                  to "4K Film İzle | Exo"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val jsonResponse =
            makeInatRequest(request.data) ?: return newHomePageResponse(request.name, emptyList())

        val searchResults = getSearchResponseList(jsonResponse)

        for (searchResponse in searchResults) {
            val url = searchResponse.url
            if (!urlToSearchResponse.containsKey(url)) {
                urlToSearchResponse[url] = searchResponse
            }
        }

        // Return a HomePageResponse with the parsed results
        return newHomePageResponse(request.name, searchResults)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (urlToSearchResponse.isEmpty()) {
            for (pageData in mainPage) {
                val url = pageData.data
                val jsonResponse = makeInatRequest(url) ?: continue

                val searchResults = getSearchResponseList(jsonResponse)

                for (searchResponse in searchResults) {
                    val contentUrl = searchResponse.url
                    if (!urlToSearchResponse.containsKey(contentUrl)) {
                        urlToSearchResponse[contentUrl] = searchResponse
                    }
                }
            }
        }

        val matchingResults = mutableListOf<SearchResponse>()

        val regex = try {
            Regex(query, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            Regex(Regex.escape(query), RegexOption.IGNORE_CASE)
        }

        for ((_, searchResponse) in urlToSearchResponse) {
            if (regex.containsMatchIn(searchResponse.name)) {
                matchingResults.add(searchResponse)
            }
        }

        return matchingResults.distinctBy { it.name }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        return search(query)
    }

    override suspend fun load(url: String): LoadResponse? {
        val item = JSONObject(url)

        if (!inatContentAllowed(item)) {
            return null
        }

        if (item.has("diziType")) {
            item.getString("diziName")
            val type = item.getString("diziType")

            return when (type) {
                "dizi" -> parseTvSeriesResponse(item)
                "film" -> parseMovieResponse(item)
                else -> null
            }

        } else if (item.has("chName") && item.has("chUrl") && item.has("chImg")) {
            item.getString("chName")
            val chType = item.getString("chType")

            val loadResponse = when (chType) {
                "live_url", "cable_sh" -> parseLiveStreamLoadResponse(item)
                "tekli_regex_lb_sh_3" -> parseLiveSportsStreamLoadResponse(item)
                else -> parseMovieResponse(item)
            }
            return loadResponse
        } else {
            return null
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        Log.d("InatBox", "data: $data")
        return try {
            if (data.startsWith("[")) {
                val chContentJsonArray = JSONArray(data)
                for (i in 0 until chContentJsonArray.length()) {
                    val chContentJsonObject = chContentJsonArray.getJSONObject(i)
                    val chContent = parseToChContent(chContentJsonObject)
                    loadChContentLinks(chContent, subtitleCallback, callback)
                }
            } else {
                val chContentJsonArray = JSONObject(data)
                val chContent = parseToChContent(chContentJsonArray)
                loadChContentLinks(chContent, subtitleCallback, callback)
            }
            true
        } catch (e: Exception) {
            Log.e("InatBox", "Error on loadLinks:${e::class.simpleName} - ${e.message}")
            false
        }
    }

    private suspend fun parseTvSeriesResponse(item: JSONObject, tvType: TvType = TvType.TvSeries): LoadResponse? {
        val episodes = mutableMapOf<DubStatus, MutableList<Episode>>()
        val seasonDataList = mutableListOf<SeasonData>()

        val name = item.getString("diziName")
        val url = item.getString("diziUrl")
        val plot = item.getString("diziDetay")

        val jsonResponse = makeInatRequest(url) ?: return null
        val jsonArray = JSONArray(jsonResponse)

        try {
            for (i in 0 until jsonArray.length()) {
                val seasonItem = jsonArray.getJSONObject(i)
                val seasonName = seasonItem.getString("diziName")
                val seasonData = SeasonData(season = (i + 1), name = seasonName)
                seasonDataList.add(seasonData)

                val seasonUrl = seasonItem.getString("diziUrl")

                // Fetch the episode data for this season
                val episodeResponse = makeInatRequest(seasonUrl) ?: continue
                val episodeArray = try {
                    JSONArray(episodeResponse)
                } catch (e: Exception) {
                    Log.e("InatBox", "Failed to parse episode JSON for season: $seasonName", e)
                    continue
                }

                for (j in 0 until episodeArray.length()) {
                    try {
                        val episodeItem = episodeArray.getJSONObject(j)
                        val episodeName = episodeItem.getString("chName")
                        val episodePoster = episodeItem.getString("chImg")
                        episodes.getOrPut(DubStatus.None) { mutableListOf() }.add(
                            newEpisode(episodeItem.toString()) {
                                this.name = episodeName
                                this.posterUrl = episodePoster
                                this.season = i + 1
                                this.episode = j + 1
                            }
                        )
                    } catch (e: JSONException) {
                        continue
                    }
                }
            }

            // Get the poster URL from the first season
            val firstSeason = jsonArray.getJSONObject(0)
            val posterUrl = firstSeason.getString("diziImg")

            return newAnimeLoadResponse(
                name = name,
                url = item.toString(),
                type = tvType,
                comingSoonIfNone = false
            ) {
                this.episodes = episodes.mapValues { it.value.toList() }.toMutableMap()
                this.posterUrl = posterUrl
                this.plot = plot
                this.seasonNames = seasonDataList
            }
        } catch (e: Exception) {
            Log.e(
                "InatBox",
                "Failed to parse TV series response: ${e.message}\nStacktrace:${
                    e.stackTrace.joinToString("\n")
                }"
            )
            return null
        }
    }

    private suspend fun parseMovieResponse(item: JSONObject): LoadResponse? {
        try {
            if (item.has("diziType")) {
                val name = item.getString("diziName")
                val url = item.getString("diziUrl")
                val posterUrl = item.getString("diziImg")
                val plot = item.getString("diziDetay")

                val jsonResponse = makeInatRequest(url) ?: return null
                val jsonArray = JSONArray(jsonResponse)

                return newMovieLoadResponse(name = name,url = item.toString(), type = TvType.Movie, dataUrl = jsonArray.toString()){
                    this.posterUrl = posterUrl
                    this.plot = plot
                }
            } else {
                val name = item.getString("chName")
                item.getString("chUrl")
                val posterUrl = item.getString("chImg")
                return newMovieLoadResponse(name, item.toString(), TvType.Movie, item.toString()) {
                    this.posterUrl = posterUrl
                }
            }
        } catch (e: Exception) {
            Log.e("InatBox", "Failed to parse movie response: ${e.message}")
            return null
        }
    }

    private suspend fun parseLiveSportsStreamLoadResponse(item: JSONObject): LiveStreamLoadResponse? {
        try {
            val chContent = parseToChContent(item)
            val posterUrl = chContent.chImg

            return newLiveStreamLoadResponse(name, item.toString(), item.toString()) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("InatBox", "Failed to parse sports live stream response: ${e.message}")
            return null
        }
    }

    private suspend fun parseLiveStreamLoadResponse(item: JSONObject): LiveStreamLoadResponse? {
        try {
            val chContent = parseToChContent(item)
            val name = chContent.chName
            val posterUrl = chContent.chImg

            return newLiveStreamLoadResponse(name, item.toString(), item.toString()) {
                this.posterUrl = posterUrl
            }
        } catch (e: Exception) {
            Log.e("InatBox", "Failed to parse movie response: ${e.message}")
            return null
        }
    }

    private fun inatContentAllowed(item: JSONObject): Boolean {
        val type: String = if (item.has("diziType")) {
            item.getString("diziType")
        } else {
            item.getString("chType")
        }

        return when (type) {
            "link", "web" -> false
            else -> true
        }
    }

    private fun String.vkSourceFix(): String {
        if (this.startsWith("act")) {
            return "https://vk.com/al_video.php?${this}"
        }
        return this
    }

    private fun parseToChContent(item: JSONObject): ChContent {
        return ChContent(
            chName = item.getString("chName"),
            chUrl = item.getString("chUrl").vkSourceFix(),
            chImg = item.getString("chImg"),
            chHeaders = item.getString("chHeaders"),
            chReg = item.getString("chReg"),
            chType = item.getString("chType")
        )
    }

    private suspend fun loadChContentLinks(chContent: ChContent, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit){
        val chType = chContent.chType
        val contentToProcess : ChContent

        if(chType == "tekli_regex_lb_sh_3"){
            val name = chContent.chName
            val url = chContent.chUrl
            val posterUrl = chContent.chImg
            val headers = chContent.chHeaders
            val reg = chContent.chReg
            val type = chContent.chType

            val jsonResponse = runCatching { makeInatRequest(url) }.getOrNull()
    ?: getJsonFromEncryptedInatResponse(app.get(url).body?.string() ?: "") ?: return
            val firstItem = JSONObject(jsonResponse)
            firstItem.put("chHeaders", headers)
            firstItem.put("chReg", reg)
            firstItem.put("chName",name)
            firstItem.put("chImg",posterUrl)
            firstItem.put("chType",type)
            contentToProcess = parseToChContent(firstItem)
        } else{
            contentToProcess = chContent
        }

        val sourceUrl = contentToProcess.chUrl

        val headers: MutableMap<String, String> = mutableMapOf()
        try {
            val chHeaders = contentToProcess.chHeaders
            val chReg = contentToProcess.chReg
            if (chHeaders != "null") {
                val jsonHeaders = JSONArray(chHeaders).getJSONObject(0)
                for (entry in jsonHeaders.keys()) {
                    headers[entry] = jsonHeaders[entry].toString()
                }
            }
            if (chReg != "null") {
                val jsonReg = JSONArray(chReg).getJSONObject(0)
                val cookie = jsonReg.getString("playSH2")
                headers["Cookie"] = cookie
            }
        } catch (_: Exception) {

        }

        val extractorFound =
            if (sourceUrl.contains("dzen.ru")) {
                 loadExtractor(sourceUrl,subtitleCallback,callback)
             } else {
                 loadExtractor(sourceUrl, headers["Referer"], subtitleCallback) {
                     callback.invoke(
                         ExtractorLink(
                             source = it.source,
                             name = contentToProcess.chName,
                             url = it.url,
                             referer = it.referer,
                             quality = it.quality,
                             headers = it.headers,
                             type = it.type
                         )
                     )
                 }
            }

        //When no extractor found, try to load as generic
        if (!extractorFound) {
            callback.invoke(
                ExtractorLink(
                    source = this.name,
                    name = contentToProcess.chName,
                    url = sourceUrl,
                    referer = "",
                    quality = Qualities.Unknown.value,
                    headers = headers,
                    type = if(sourceUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else if(sourceUrl.contains(".mpd")) ExtractorLinkType.DASH else ExtractorLinkType.VIDEO
                )
            )
        }
    }

    private suspend fun makeInatRequest(url: String): String? {
        // Extract hostname using URI
        val hostName = try {
            URI(url).host ?: throw IllegalArgumentException("Invalid URL: $url")
        } catch (e: Exception) {
            Log.e("InatBox", "Failed to extract hostname from URL: $url", e)
            return null
        }

        val headers = mapOf(
            "Cache-Control" to "no-cache",
            "Content-Length" to "37",
            "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
            "Host" to hostName,
            "Referer" to "https://speedrestapi.com/",
            "X-Requested-With" to "com.bp.box"
        )

        val requestBody = "1=${aesKey}&0=${aesKey}"

        val interceptor = Interceptor { chain ->
            val request = chain.request()
            val newRequest = request.newBuilder().header("User-Agent", "speedrestapi").build()
            chain.proceed(newRequest)
        }

        val response = app.post(
            url = url,
            headers = headers,
            requestBody = requestBody.toRequestBody(contentType = "application/x-www-form-urlencoded; charset=UTF-8".toMediaType()),
            interceptor = interceptor
        )

        if (response.isSuccessful) {
            val encryptedResponse = response.body?.string() ?: ""
            // Log.d("InatBox", "Encrypted response: ${encryptedResponse}")
            return getJsonFromEncryptedInatResponse(encryptedResponse)
        } else {
            Log.e("InatBox", "Request failed")
            return null
        }
    }

    private fun getJsonFromEncryptedInatResponse(response: String): String? {
        try {
            val algorithm = "AES/CBC/PKCS5Padding"
            val keySpec = SecretKeySpec(aesKey.toByteArray(), "AES")

            // First decryption iteration
            val cipher1 = Cipher.getInstance(algorithm)
            cipher1.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(aesKey.toByteArray()))
            val firstIterationData =
                cipher1.doFinal(Base64.decode(response.split(":")[0], Base64.DEFAULT))

            // Second decryption iteration
            val cipher2 = Cipher.getInstance(algorithm)
            cipher2.init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(aesKey.toByteArray()))
            val secondIterationData = cipher2.doFinal(
                Base64.decode(
                    String(firstIterationData).split(":")[0],
                    Base64.DEFAULT
                )
            )

            // Parse JSON
            val jsonString = String(secondIterationData)
            return jsonString
        } catch (e: Exception) {
            Log.e("InatBox", "Decryption failed: ${e.message}")
            return null
        }
    }

    private fun getSearchResponseList(jsonResponse: String): List<SearchResponse> {
        val searchResults = mutableListOf<SearchResponse>()
        try {
            val jsonArray = JSONArray(jsonResponse)

            for (i in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(i)

                if (!inatContentAllowed(item)) {
                    continue
                }

                //Let's pass item directly to the next step
                if (item.has("diziType")) {
                    val name = item.getString("diziName")
                    val type = item.getString("diziType")
                    val posterUrl = item.getString("diziImg")

                    val searchResponse = when (type) {
                        "dizi" -> newTvSeriesSearchResponse(name, item.toString()) {
                            this.posterUrl = posterUrl
                        }

                        "film" -> newMovieSearchResponse(name, item.toString()) {
                            this.posterUrl = posterUrl
                        }

                        else -> null // Ignore unsupported types
                    }
                    searchResponse?.let { searchResults.add(it) }
                } else if (item.has("chName") && item.has("chUrl") && item.has("chImg")) {
                    // Handle the case where diziType is missing but chName, chUrl, and chImg are present
                    val name = item.getString("chName")
                    val posterUrl = item.getString("chImg")
                    val chType = item.getString("chType")

                    val searchResponse = when (chType) {
                        "live_url", "tekli_regex_lb_sh_3" -> newLiveSearchResponse(name, item.toString(), TvType.Live) {
                            this.posterUrl = posterUrl
                        }

                        else -> newMovieSearchResponse(name, item.toString()) {
                            this.posterUrl = posterUrl
                        }
                    }
                    searchResults.add(searchResponse)
                }
            }
        } catch (e: Exception) {
            Log.e("InatBox", "Failed to parse JSON response: ${e.message}")
        }

        return searchResults
    }
}
