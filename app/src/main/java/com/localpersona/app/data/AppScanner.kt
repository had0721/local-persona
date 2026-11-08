package com.localpersona.app.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class InstalledApp(
    val pkg: String,
    val system: Boolean,
)

object AppScanner {

    /**
     * 只取包名，不取应用名 —— loadLabel 会把这里变成 1~3 秒的开销。
     *
     * 能读到哪些应用由 AndroidManifest 里的 <queries> 决定：那里声明了
     * android.intent.action.MAIN，所以所有能从桌面启动的应用都对本应用可见，
     * 不需要 QUERY_ALL_PACKAGES。
     */
    @Suppress("DEPRECATION")
    fun scan(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val infos = try {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                pm.getInstalledPackages(0)
            }
        } catch (t: Throwable) {
            emptyList()
        }
        return infos.map { pi ->
            val flags = pi.applicationInfo?.flags ?: 0
            InstalledApp(pi.packageName, (flags and ApplicationInfo.FLAG_SYSTEM) != 0)
        }.distinctBy { it.pkg }
    }
}
