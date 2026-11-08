package com.localpersona.app.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * 模型仓库。APK 里不放权重（500MB+），首次启动从镜像下载。
 *
 * 下载走 5 路 Range 分段并发，写同一个文件的不同偏移
 * （FileChannel 的位置写是线程安全的）。实测能比单流快 2~4 倍。
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
        approxBytes = 532_517_120L,
        mirrors = listOf(
            "https://hf-mirror.com/unsloth/Qwen3.5-0.8B-GGUF/resolve/main/Qwen3.5-0.8B-Q4_K_M.gguf?download=true",
        ),
    )

    val DEFAULT = QWEN_0_8B_Q4KM

    private const val SEGMENTS = 5
    private const val UA = "Mozilla/5.0 (LocalPersona)"
    private const val CHUNK = 128 * 1024

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
    ): File {
        if (isReady(ctx, spec)) return modelFile(ctx, spec)

        val target = modelFile(ctx, spec)
        val part = File(modelDir(ctx), "${spec.fileName}.part")
        var lastError: Throwable? = null

        for (url in spec.mirrors) {
            try {
                fetch(url, part, target, spec, onProgress)
                return target
            } catch (t: Throwable) {
                lastError = t
                runCatching { part.delete() }
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    private suspend fun fetch(
        url: String,
        part: File,
        target: File,
        spec: ModelSpec,
        onProgress: (Progress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val total = probeLength(url) ?: spec.approxBytes
        if (total <= 0) throw IllegalStateException("无法获取文件大小")

        val done = AtomicLong(0)
        var lastReport = 0L

        RandomAccessFile(part, "rw").use { raf ->
            raf.setLength(total)
            val channel = raf.channel
            val segSize = (total + SEGMENTS - 1) / SEGMENTS

            coroutineScope {
                (0 until SEGMENTS).map { i ->
                    async(Dispatchers.IO) {
                        val start = i * segSize
                        if (start >= total) return@async
                        val end = minOf(start + segSize, total) - 1
                        var pos = start
                        var attempt = 0
                        while (pos <= end) {
                            attempt++
                            try {
                                pos = pullRange(url, pos, end, channel) { n ->
                                    val cur = done.addAndGet(n.toLong())
                                    val now = System.currentTimeMillis()
                                    if (now - lastReport > 250) {
                                        lastReport = now
                                        onProgress(Progress(cur, total))
                                    }
                                }
                            } catch (t: Throwable) {
                                if (attempt >= 3) throw t
                                Thread.sleep(800L * attempt)
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        onProgress(Progress(total, total))
        if (part.length() < total) throw IllegalStateException("下载不完整")

        if (target.exists()) target.delete()
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        if (target.length() <= 0) throw IllegalStateException("下载结果为空")
    }

    /** 从 pos 拉到 end（含），返回新的 pos。 */
    private fun pullRange(
        url: String,
        from: Long,
        to: Long,
        channel: java.nio.channels.FileChannel,
        onBytes: (Int) -> Unit,
    ): Long {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Range", "bytes=$from-$to")
        }
        try {
            if (conn.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw IllegalStateException("分段请求被拒绝: HTTP ${conn.responseCode}")
            }
            var pos = from
            val buf = ByteArray(CHUNK)
            conn.inputStream.use { input ->
                while (pos <= to) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    channel.write(ByteBuffer.wrap(buf, 0, n), pos)
                    pos += n
                    onBytes(n)
                }
            }
            return pos
        } finally {
            conn.disconnect()
        }
    }

    private fun probeLength(url: String): Long? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Range", "bytes=0-0")
        }
        return try {
            val range = conn.getHeaderField("Content-Range")
            val total = range?.substringAfter('/', "")?.toLongOrNull()
            if (total != null && total > 0) total else conn.contentLengthLong.takeIf { it > 0 }
        } catch (t: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
