package com.localpersona.app.net

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 极简搜索：只要“页面上出现过哪些词”，不要结构化的结果列表。
 * 因此任何能返回 HTML 的引擎都能用，解析成本接近 0。
 *
 * 两个引擎并发发，谁先有内容用谁；整个过程由外层硬超时兜住。
 */
object WebSearch {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    suspend fun text(query: String, timeoutMs: Int = 1400): String? =
        withTimeoutOrNull(timeoutMs.toLong()) {
            coroutineScope {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val bing = async { fetch("https://cn.bing.com/search?q=$encoded&setlang=zh-CN", timeoutMs) }
                val baidu = async { fetch("https://www.baidu.com/s?ie=utf-8&wd=$encoded", timeoutMs) }
                val hits = listOf(bing, baidu).mapNotNull { it.await() }
                hits.firstOrNull { it.length > 400 } ?: hits.firstOrNull()
            }
        }

    private suspend fun fetch(url: String, timeoutMs: Int): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val raw = conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                val sb = StringBuilder()
                var total = 0
                while (total < 400_000) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    sb.append(String(buf, 0, n, Charsets.UTF_8))
                    total += n
                }
                sb.toString()
            }
            strip(raw).takeIf { it.length > 200 }
        } catch (t: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun strip(html: String): String =
        html.replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", " ")
            .replace("&gt;", " ")
            .replace(Regex("[ \\t\\u00a0]+"), " ")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
}
