package com.localpersona.app.data

import android.app.ActivityManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.StatFs
import java.util.Locale
import java.util.TimeZone

/**
 * 设备事实：全部来自 Build.* 与系统 API，不需要任何运行时权限。
 */
data class DeviceFacts(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdk: Int,
    val abis: List<String>,
    val cores: Int,
    val ramGb: Double,
    val storageGb: Double,
    val screenInch: Double,
    val densityDpi: Int,
    val fontScale: Float,
    val languageTag: String,
    val timeZone: String,
) {
    /** 压成两三行，喂给模型的只有这些。 */
    fun compact(): String {
        val lang = if (languageTag.startsWith("zh")) "中文" else languageTag
        val screen = if (screenInch > 0) String.format(Locale.US, "%.1f 英寸", screenInch) else "未知尺寸"
        return buildString {
            append("设备：${manufacturer.ifBlank { brand }} $model（代号 $device）\n")
            append("系统：Android $androidRelease（API $sdk），${abis.firstOrNull() ?: "arm64"}\n")
            append(
                "硬件：$cores 核 / ${"%.1f".format(Locale.US, ramGb)} GB 内存 / " +
                    "${"%.0f".format(Locale.US, storageGb)} GB 存储 / $screen / ${densityDpi}dpi\n"
            )
            append("语言：$lang，时区：$timeZone")
            if (fontScale > 1.08f) append("，系统字体已放大 ${"%.2f".format(Locale.US, fontScale)} 倍")
            if (fontScale < 0.92f) append("，系统字体已缩小")
        }
    }

    companion object {
        fun read(ctx: Context): DeviceFacts {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            val stat = StatFs(ctx.filesDir.absolutePath)
            val dm = ctx.resources.displayMetrics
            val cfg = ctx.resources.configuration
            val wIn = dm.widthPixels / dm.xdpi
            val hIn = dm.heightPixels / dm.ydpi
            val diag = if (wIn > 0 && hIn > 0) {
                kotlin.math.sqrt((wIn * wIn + hIn * hIn).toDouble())
            } else {
                0.0
            }

            val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cfg.locales.get(0) ?: Locale.getDefault()
            } else {
                @Suppress("DEPRECATION") cfg.locale ?: Locale.getDefault()
            }

            return DeviceFacts(
                manufacturer = Build.MANUFACTURER.orEmpty(),
                brand = Build.BRAND.orEmpty(),
                model = Build.MODEL.orEmpty(),
                device = Build.DEVICE.orEmpty(),
                androidRelease = Build.VERSION.RELEASE.orEmpty(),
                sdk = Build.VERSION.SDK_INT,
                abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
                cores = Runtime.getRuntime().availableProcessors(),
                ramGb = mi.totalMem / 1024.0 / 1024.0 / 1024.0,
                storageGb = stat.blockCountLong * stat.blockSizeLong / 1024.0 / 1024.0 / 1024.0,
                screenInch = diag,
                densityDpi = dm.densityDpi,
                fontScale = cfg.fontScale,
                languageTag = locale.toLanguageTag().ifBlank { "zh-CN" },
                timeZone = TimeZone.getDefault().id,
            )
        }
    }
}
