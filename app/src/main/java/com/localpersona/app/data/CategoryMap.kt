package com.localpersona.app.data

import android.content.Context

data class AppProfile(
    val category: String,
    val ageBand: String = "",
    val name: String = "",
    val note: String = "",
)

/**
 * 包名 → 类目（+ 常见应用的人群标签）。
 *
 * 这是整套方案里最关键的提速部件：把上百个包名在本地压成一小组统计，
 * 0.8B 模型只负责写作，不用去“认识”每一个包名。
 *
 * 表里已经写了 60 个常见应用的典型年龄和人群备注，
 * 命中的就直接用，不再联网；联网只留给真正认不出来的长尾应用。
 */
class CategoryMap private constructor(
    private val exact: Map<String, AppProfile>,
    private val prefixes: List<Pair<String, AppProfile>>,
) {

    fun profileOf(pkg: String): AppProfile? {
        exact[pkg]?.let { return it }
        var best: AppProfile? = null
        var bestLen = 0
        for ((p, prof) in prefixes) {
            if (pkg.startsWith(p) && p.length > bestLen) {
                best = prof
                bestLen = p.length
            }
        }
        if (best != null) return best
        return heuristic(pkg)
    }

    fun categoryOf(pkg: String): String? = profileOf(pkg)?.category

    private fun heuristic(pkg: String): AppProfile? {
        val p = pkg.lowercase()
        val cat = when {
            p.contains("vpn") || p.contains("proxy") || p.contains("clash") ||
                p.contains("v2ray") || p.contains("shadowsocks") || p.contains("socks") -> "网络工具"
            p.contains("bank") || p.contains("banking") -> "银行"
            p.contains("wallet") || p.contains("coin") -> "加密金融"
            p.contains("browser") -> "浏览器"
            p.contains("inputmethod") || p.contains("ime") -> "输入法"
            p.contains("music") || p.contains("player") -> "音乐"
            p.contains("camera") -> "摄影"
            p.contains("weather") -> "天气"
            p.contains("news") || p.contains("reader") -> "资讯"
            p.contains("launcher") || p.contains("theme") -> "美化"
            p.contains("game") || p.contains(".gp") -> "游戏"
            p.startsWith("com.google.") -> "Google 服务"
            p.startsWith("com.android.") || p.startsWith("com.miui.") ||
                p.startsWith("com.coloros.") || p.startsWith("com.oplus.") ||
                p.startsWith("com.huawei.") || p.startsWith("com.samsung.") -> "系统组件"
            else -> null
        }
        return cat?.let { AppProfile(it) }
    }

    companion object {
        private const val ASSET = "app_map.tsv"

        fun load(ctx: Context): CategoryMap {
            val exact = HashMap<String, AppProfile>(1024)
            val prefixes = ArrayList<Pair<String, AppProfile>>(256)
            runCatching {
                ctx.assets.open(ASSET).bufferedReader().useLines { lines ->
                    for (raw in lines) {
                        val line = raw.trim()
                        if (line.isEmpty() || line.startsWith("#")) continue
                        val parts = line.split('\t')
                        if (parts.size < 2) continue
                        val key = parts[0].trim()
                        val cat = parts[1].trim()
                        if (key.isEmpty() || cat.isEmpty()) continue
                        val profile = AppProfile(
                            category = cat,
                            ageBand = parts.getOrNull(2)?.trim().orEmpty(),
                            name = parts.getOrNull(3)?.trim().orEmpty(),
                            note = parts.getOrNull(4)?.trim().orEmpty(),
                        )
                        if (key.endsWith(".*")) {
                            prefixes.add(key.dropLast(2) to profile)
                        } else {
                            exact[key] = profile
                        }
                    }
                }
            }
            if (prefixes.isEmpty()) {
                prefixes.add("com.tencent." to AppProfile("腾讯系"))
            }
            return CategoryMap(exact, prefixes)
        }
    }
}
