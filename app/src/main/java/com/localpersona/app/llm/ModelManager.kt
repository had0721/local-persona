package com.localpersona.app.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * 模型仓库。APK 里不放权重（500MB+），首次启动从镜像下载，支持断点续传。
 * hf-mirror.com 是 HuggingFace 的国内镜像，实测在手机网络下可直连。
 */
object ModelManager {

    data class ModelSpec(
        val id: String,
        val label: String,
        val fileName: String,
        val approxBytes: Long,
        val mirrors: List<String>,
    )

    val QWEN_0_8B_Q4KM = ModelSpec(
        id = "qwen3.5-0.8b-q4km",
        label = "Qwen3.5-0.8B · Q4_K_M（约 508 MB）",
        fileName = "Qwen3.5-0.8B-Q4_K_M.gguf",
        approxBytes = 532_464_000L,
        mirrors = listOf(
            "https://hf-mirror.com/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf?download=true",
        ),
    )

    val DEFAULT = QWEN_0_8B_Q4KM

    fun modelDir(ctx: Context): File =
        File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "models").also { it.mkdirs() }

    fun modelFile(ctx: Context, spec: ModelSpec = DEFAULT): File = File(modelDir(ctx), spec.fileName)

    fun isReady(ctx: Context, spec: ModelSpec = DEFAULT): Boolean {
        val f = modelFile(ctx, spec)
        return f.exists() && f.length() > spec.approxBytes * 8 / 10
    }

    fun delete(ctx: Context, spec: ModelSpec = DEFAULT) {
        modelFile(ctx, spec).delete()
        File(modelDir(ctx), "${spec.fileName}.part").delete()
    }

    data class Progress(val downloaded: Long, val total: Long) {
        val fraction: Float
            get() = if (total <= 0) 0f else (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f)
    }

    suspend fun download(
        ctx: Context,
        spec: ModelSpec = DEFAULT,
        onProgress: (Progress) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val target = modelFile(ctx, spec)
        if (isReady(ctx, spec)) return@withContext target

        val part = File(modelDir(ctx), "${spec.fileName}.part")
        var lastError: Throwable? = null

        for (url in spec.mirrors) {
            try {
                return@withContext fetch(url, part, target, spec, onProgress)
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    private fun fetch(
        url: String,
        part: File,
        target: File,
        spec: ModelSpec,
        onProgress: (Progress) -> Unit,
    ): File {
        var startAt = if (part.exists()) part.length() else 0L

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Mozilla/5.0 (LocalPersona)")
            if (startAt > 0) setRequestProperty("Range", "bytes=$startAt-")
        }

        val code = conn.responseCode
        val append = code == HttpURLConnection.HTTP_PARTIAL
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            conn.disconnect()
            // 服务器不认断点就从头来
            part.delete()
            throw IllegalStateException("HTTP $code")
        }
        if (!append) startAt = 0L

        val total = if (conn.contentLengthLong > 0) conn.contentLengthLong + startAt else spec.approxBytes

        RandomAccessFile(part, "rw").use { raf ->
            raf.seek(startAt)
            var written = startAt
            val buf = ByteArray(256 * 1024)
            conn.inputStream.use { input ->
                var lastReport = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    raf.write(buf, 0, n)
                    written += n
                    if (written - lastReport > 2L * 1024 * 1024) {
                        lastReport = written
                        onProgress(Progress(written, total))
                    }
                }
            }
            onProgress(Progress(written, total))
        }
        conn.disconnect()

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        if (target.length() <= 0) throw IllegalStateException("下载结果为空")
        return target
    }
}
