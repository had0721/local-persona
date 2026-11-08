package com.localpersona.app.net

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 只用搜索补两件事：
 *   1. 本地映射表没认出来的应用 —— 它是什么类目；
 *   2. 模型可能没见过的机型的定位。
 *
 * 提速的三条：
 *   - 只查前 6 个未归类包，不多查；
 *   - 与模型加载并行，整体硬超时 2.4 秒，超时就用已有结果；
 *   - 结果落盘缓存 30 天，第二次分析 0 毫秒。
 */
data class Enrichment(
    val categories: Map<String, String> = emptyMap(),
    val deviceNote: String? = null,
    val fromCache: Int = 0,
    val fetched: Int = 0,
    val elapsedMs: Long = 0,
) {
    val touchedAny: Boolean get() = categories.isNotEmpty() || deviceNote != null
}

class SearchResolver(private val cache: SearchCache) {

    companion object {
        const val MAX_LOOKUPS = 6
        const val BUDGET_MS = 2400L
        private const val PER_QUERY_MS = 1400
    }

    suspend fun enrich(
        unmapped: List<String>,
        deviceModel: String,
        budgetMs: Long = BUDGET_MS,
    ): Enrichment {
        val started = System.currentTimeMillis()
        val resolved = LinkedHashMap<String, String>()
        val todo = ArrayList<String>(MAX_LOOKUPS)

        for (pkg in unmapped) {
            val cached = cache.get(pkg)
            if (cached != null) {
                if (cached != UNKNOWN) resolved[pkg] = cached
            } else if (todo.size < MAX_LOOKUPS) {
                todo.add(pkg)
            }
        }

        val deviceCached = cache.get(deviceKey(deviceModel))
        var deviceNote: String? = deviceCached?.takeIf { it != UNKNOWN }
        var fetched = 0

        withTimeoutOrNull(budgetMs) {
            coroutineScope {
                val deviceJob = if (deviceCached == null) {
                    async { deviceSummary(deviceModel) }
                } else {
                    null
                }

                // 6 个查询同时发，整体只等最慢的那个，而不是 6 倍
                val lookups = todo.map { pkg -> pkg to async { classify(pkg) } }
                for ((pkg, job) in lookups) {
                    val cat = job.await()
                    if (cat != null) {
                        resolved[pkg] = cat
                        cache.put(pkg, cat)
                        fetched++
                    } else {
                        cache.put(pkg, UNKNOWN)
                    }
                }

                if (deviceJob != null) {
                    val note = deviceJob.await()
                    if (note != null) {
                        deviceNote = note
                        cache.put(deviceKey(deviceModel), note)
                    } else {
                        cache.put(deviceKey(deviceModel), UNKNOWN)
                    }
                }
            }
        }

        return Enrichment(
            categories = resolved,
            deviceNote = deviceNote,
            fromCache = resolved.size - fetched,
            fetched = fetched,
            elapsedMs = System.currentTimeMillis() - started,
        )
    }

    private suspend fun classify(pkg: String): String? {
        val token = tokenOf(pkg) ?: return null
        val text = WebSearch.text("$token 应用 app 是什么 功能", PER_QUERY_MS) ?: return null
        return Categories.infer(text)
    }

    private suspend fun deviceSummary(model: String): String? {
        if (model.isBlank()) return null
        val text = WebSearch.text("「$model」 手机 参数 定位 价格", PER_QUERY_MS) ?: return null
        val markers = listOf("旗舰", "高端", "中端", "千元", "性价比", "影像", "游戏", "折叠", "商务", "入门", "轻薄")
        for (sentence in text.split('。', '！', '？', '\n')) {
            val s = sentence.trim()
            if (s.length in 12..70 && markers.any { s.contains(it) }) {
                return s.take(60)
            }
        }
        return null
    }

    private fun tokenOf(pkg: String): String? {
        val parts = pkg.split('.').filter { it.length >= 3 }
        val stop = setOf("com", "cn", "org", "net", "android", "app", "apps", "mobile", "client", "phone", "lite", "global", "main")
        return parts.filter { it.lowercase() !in stop }
            .maxByOrNull { it.length }
            ?.takeIf { it.any { c -> c.isLetter() } }
    }

    private fun deviceKey(model: String) = "device:$model"
}

private const val UNKNOWN = "未归类"

/** 搜索页文本 → 类目。命中两个以上关键词才算数，避免误判。 */
object Categories {
    private val rules: List<Pair<String, List<String>>> = listOf(
        "电商" to listOf("购物", "商城", "电商", "下单", "淘宝", "京东", "拼团", "优惠券"),
        "社交" to listOf("社交", "聊天", "交友", "社区", "论坛", "问答", "发帖"),
        "短视频" to listOf("短视频", "刷视频", "抖音", "快手"),
        "长视频" to listOf("影视", "追剧", "播放器", "视频网站", "剧集"),
        "音乐" to listOf("音乐", "听歌", "电台", "播客", "歌单"),
        "阅读" to listOf("小说", "阅读", "电子书", "书城", "连载"),
        "资讯" to listOf("新闻", "资讯", "头条", "热点"),
        "游戏" to listOf("游戏", "手游", "网游", "抽卡", "对战"),
        "银行" to listOf("银行", "信用卡", "储蓄", "转账", "网点"),
        "证券" to listOf("证券", "股票", "基金", "行情", "开户"),
        "支付" to listOf("支付", "钱包", "收款", "扫码付"),
        "加密金融" to listOf("加密", "比特币", "虚拟货币", "数字资产", "交易所"),
        "外卖" to listOf("外卖", "点餐", "配送", "团购", "餐厅"),
        "出行" to listOf("打车", "出行", "骑行", "公交", "顺风车"),
        "地图" to listOf("地图", "导航", "路线", "实时路况"),
        "旅行" to listOf("旅游", "酒店", "机票", "民宿", "攻略"),
        "健康" to listOf("健身", "运动", "健康", "医疗", "挂号", "问诊"),
        "摄影" to listOf("相机", "拍照", "修图", "滤镜", "剪辑", "视频编辑"),
        "办公" to listOf("办公", "文档", "表格", "协作", "会议"),
        "效率" to listOf("笔记", "待办", "效率", "清单", "时间管理"),
        "开发" to listOf("开发", "终端", "代码", "编程", "命令行", "ide"),
        "学习" to listOf("学习", "课程", "背单词", "考试", "题库", "网课"),
        "输入法" to listOf("输入法", "键盘", "打字", "词库"),
        "浏览器" to listOf("浏览器", "上网", "网页"),
        "网络工具" to listOf("代理", "加速", "节点", "科学上网", "网络工具"),
        "房产" to listOf("租房", "买房", "房产", "楼盘", "二手房"),
        "招聘" to listOf("招聘", "求职", "找工作", "简历"),
        "母婴" to listOf("母婴", "育儿", "宝宝", "孕期"),
        "宠物" to listOf("宠物", "猫", "狗", "铲屎"),
        "汽车" to listOf("车主", "汽车", "新车", "试驾", "充电桩"),
        "票务" to listOf("演出", "演唱会", "票务", "电影票"),
        "AI" to listOf("人工智能", "大模型", "ai 助手", "智能问答"),
        "工具" to listOf("工具", "清理", "文件管理", "压缩"),
    )

    fun infer(text: String): String? {
        val lower = text.lowercase()
        var best: String? = null
        var bestScore = 0
        for ((cat, words) in rules) {
            val score = words.count { lower.contains(it) }
            if (score > bestScore) {
                bestScore = score
                best = cat
            }
        }
        return if (bestScore >= 2) best else null
    }
}

class SearchCache(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("search_cache", Context.MODE_PRIVATE)

    fun get(key: String): String? {
        val value = sp.getString(key, null) ?: return null
        val ts = sp.getLong(tsKey(key), 0L)
        if (System.currentTimeMillis() - ts > TTL_MS) {
            sp.edit().remove(key).remove(tsKey(key)).apply()
            return null
        }
        return value
    }

    fun put(key: String, value: String) {
        sp.edit().putString(key, value).putLong(tsKey(key), System.currentTimeMillis()).apply()
    }

    private fun tsKey(key: String) = "$key#ts"

    companion object {
        private const val TTL_MS = 30L * 24 * 60 * 60 * 1000
    }
}
