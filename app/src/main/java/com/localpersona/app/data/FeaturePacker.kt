package com.localpersona.app.data

import java.util.Locale

/**
 * 分析用的“种子”。这是唯一会被送进模型的东西，控制在 300 token 上下。
 */
data class AnalysisSeed(
    val facts: DeviceFacts,
    val thirdPartyCount: Int,
    val systemCount: Int,
    val categories: List<Pair<String, Int>>,
    val signals: List<String>,
    val unmappedCount: Int,
    val unmappedPackages: List<String> = emptyList(),
) {
    fun promptBlock(deviceNote: String? = null): String = buildString {
        append(facts.compact())
        if (!deviceNote.isNullOrBlank()) {
            append("\n机型参考：").append(deviceNote).append("（检索自公开资料）")
        }
        append("\n\n")
        append("已安装第三方应用 $thirdPartyCount 个（另有 $systemCount 个系统预装，已忽略）。\n")
        if (categories.isNotEmpty()) {
            val dist = categories.joinToString("、") { "${it.first} ${it.second}" }
            append("类目分布（前 ${categories.size}）：$dist。\n")
        }
        if (signals.isNotEmpty()) {
            append("显著信号：\n")
            for (s in signals) append("- $s\n")
        }
        if (unmappedCount > 0) {
            append("未归类应用 $unmappedCount 个（多为长尾工具或小众应用）。\n")
        }
    }

    fun summary(): String {
        if (categories.isEmpty()) return "已扫描 $thirdPartyCount 个应用"
        val top = categories.take(3).joinToString("、") { it.first }
        return "$thirdPartyCount 个应用 · 以 $top 为主"
    }
}

object FeaturePacker {

    private const val MAX_UNMAPPED = 12

    fun pack(apps: List<InstalledApp>, map: CategoryMap, facts: DeviceFacts): AnalysisSeed {
        val thirdParty = apps.filter { !it.system }
        val system = apps.size - thirdParty.size

        val counts = HashMap<String, Int>(64)
        val unmappedPkgs = ArrayList<String>(32)
        for (app in thirdParty) {
            val c = map.categoryOf(app.pkg)
            if (c == null) {
                if (unmappedPkgs.size < MAX_UNMAPPED) unmappedPkgs.add(app.pkg)
            } else {
                counts[c] = (counts[c] ?: 0) + 1
            }
        }

        val ordered = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(12)
            .map { it.key to it.value }

        val signals = SignalRules.detect(thirdParty.map { it.pkg })

        return AnalysisSeed(
            facts = facts,
            thirdPartyCount = thirdParty.size,
            systemCount = system,
            categories = ordered,
            signals = signals,
            unmappedCount = thirdParty.size - counts.values.sum(),
            unmappedPackages = unmappedPkgs,
        )
    }

    /** 把联网识别出来的类目并回统计。 */
    fun merge(seed: AnalysisSeed, resolved: Map<String, String>): AnalysisSeed {
        if (resolved.isEmpty()) return seed
        val counts = LinkedHashMap<String, Int>()
        for ((cat, n) in seed.categories) counts[cat] = n
        for (cat in resolved.values) counts[cat] = (counts[cat] ?: 0) + 1
        val ordered = counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(12)
            .map { it.key to it.value }
        return seed.copy(
            categories = ordered,
            unmappedCount = (seed.unmappedCount - resolved.size).coerceAtLeast(0),
            unmappedPackages = seed.unmappedPackages.filter { it !in resolved },
        )
    }

    fun hitRate(seed: AnalysisSeed): Double {
        val total = seed.thirdPartyCount
        if (total == 0) return 0.0
        return (total - seed.unmappedCount).toDouble() / total
    }

    fun formatPercent(v: Double): String = String.format(Locale.US, "%.0f%%", v * 100)
}
