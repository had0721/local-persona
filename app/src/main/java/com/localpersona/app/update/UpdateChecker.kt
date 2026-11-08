package com.localpersona.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 更新检查。默认从仓库里的 version.json 读版本，再下载 APK。
 * 所有 GitHub 地址都能套一层镜像前缀（如 https://ghfast.top），
 * 用于国内直连 github.com 不稳定的情况。
 */
object UpdateChecker {

    const val DEFAULT_MANIFEST =
        "https://raw.githubusercontent.com/had0721/local-persona/main/update/version.json"

    data class Info(
        val versionName: String,
        val versionCode: Int,
        val apkUrl: String,
        val notes: String,
    )

    fun withMirror(url: String, mirror: String): String =
        if (mirror.isBlank()) url else mirror.trimEnd('/') + "/" + url

    suspend fun check(manifestUrl: String, mirror: String): Info? = withContext(Dispatchers.IO) {
        val body = runCatching { fetchText(withMirror(manifestUrl, mirror)) }.getOrNull() ?: return@withContext null
        runCatching {
            val o = JSONObject(body)
            Info(
                versionName = o.optString("versionName"),
                versionCode = o.optInt("versionCode"),
                apkUrl = o.optString("apkUrl"),
                notes = o.optString("notes"),
            )
        }.getOrNull()?.takeIf { it.versionCode > 0 && it.apkUrl.isNotBlank() }
    }

    suspend fun download(
        apkUrl: String,
        mirror: String,
        dest: File,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        dest.parentFile?.mkdirs()
        val conn = (URL(withMirror(apkUrl, mirror)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LocalPersona")
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            dest.outputStream().use { out ->
                val buf = ByteArray(128 * 1024)
                var written = 0L
                var last = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    written += n
                    if (total > 0 && written - last > 1024 * 1024) {
                        last = written
                        onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        conn.disconnect()
        dest
    }

    private fun fetchText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "LocalPersona")
        }
        if (conn.responseCode != HttpURLConnection.HTTP_OK) {
            conn.disconnect()
            throw IllegalStateException("HTTP ${conn.responseCode}")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
