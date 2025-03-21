// ! Bu araç @kraptor123 tarafından yazılmıştır.

package com.keyiflerolsun

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.json.JSONObject

class CloudflareKiller {
    fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        Thread.sleep(5000)
        return chain.proceed(chain.request())
    }
}

class CloudflareInterceptor(private val cloudflareKiller: CloudflareKiller) : okhttp3.Interceptor {
    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val bodyString = response.peekBody(1024 * 1024).string()
        val doc = Jsoup.parse(bodyString)
        if (doc.text().contains("Attention Required!") ||
            doc.text().contains("Güvenlik taramasından geçiriliyorsunuz. Lütfen bekleyiniz..")) {
            return cloudflareKiller.intercept(chain)
        }
        return response
    }
}

val client: OkHttpClient = OkHttpClient.Builder()
    .followRedirects(false)
    .addInterceptor(CloudflareInterceptor(CloudflareKiller()))
    .addInterceptor { chain ->
        val newRequest = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Connection", "keep-alive")
            .header("Referer", "https://anizm.net/")
            .header("Sec-Fetch-Dest", "iframe")
            .header("Sec-Fetch-Mode", "navigate")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Host", "anizm.net")
            .build()
        chain.proceed(newRequest)
    }
    .build()

fun getVideoUrls(data: String): Map<String, String> {
    val mainRequest = Request.Builder()
        .url(data)
        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
        .header("Referer", "https://anizm.net/")
        .build()

    val mainResponse = client.newCall(mainRequest).execute()
    val mainHtml = mainResponse.body.string()
    val doc = Jsoup.parse(mainHtml)
    val translatorLinks = doc.select("div#fansec.fansubSecimKutucugu a[translator]").map { it.attr("translator") }
    val allPlayerLinks = mutableMapOf<String, String>()
    translatorLinks.forEach { translatorLink ->
        var jsonText: String
        var attempts = 0
        val maxAttempts = 3
        
        while (attempts < maxAttempts) {
            attempts++
            try {
                val jsonRequest = Request.Builder()
                    .url(translatorLink)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .build()

                val jsonResponse = client.newCall(jsonRequest).execute()
                jsonText = jsonResponse.body.string()
                
                if (!jsonText.contains("Attention Required!")) {
                    val jsonData = JSONObject(jsonText)
                    val embedDoc = Jsoup.parse(jsonData.getString("data"))
                    
                    embedDoc.select("a.videoPlayerButtons[video]").forEach { element ->
                        val name = element.select("span").text()
                        val videoUrl = element.attr("video").replace("/video/", "//player/")
                        if (!allPlayerLinks.containsKey(name)) {
                            allPlayerLinks[name] = videoUrl
                        }
                    }
                    break
                }
            } catch (e: Exception) {
            }
        }
    }

    val realVideoUrls = mutableMapOf<String, String>()
    allPlayerLinks.forEach { (name, url) ->
        try {
            val redirectRequest = Request.Builder()
                .url(url)
                .header("Referer", "https://anizm.net/")
                .build()

            client.newCall(redirectRequest).execute().use { response ->
                val realUrl = response.header("Location") ?: url
                if (realUrl.contains("myvi.ru") || realUrl.contains("supervideo.tv")) {
                    return@use
                }
                if (realUrl.contains("player")) {
                    val finalRequest = Request.Builder()
                        .url(realUrl)
                        .header("Referer", "https://anizm.net/")
                        .build()

                    client.newCall(finalRequest).execute().use { finalResponse ->
                        val finalLocation = finalResponse.header("Location") ?: realUrl
                        realVideoUrls[name] = finalLocation
                    }
                } else {
                    realVideoUrls[name] = realUrl
                }
            }
        } catch (e: Exception) {
        }
    }

    return realVideoUrls
}
