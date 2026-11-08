package com.localpersona.app.llm

import com.localpersona.app.data.AnalysisSeed

/**
 * Qwen3.5 的对话格式。不用 jinja 模板，手工拼。
 *
 * 输出被约束成「总览 + 固定维度选择题 + 带依据的判断」，比让它自由写四段话稳得多。
 * thinking 关闭时，assistant 前缀后面直接给一个空的 <think></think>，省 2~3 倍 token。
 */
object PromptBuilder {

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    data class Claim(
        val judgment: String,
        val evidence: String,
        val confidence: String,
        val verified: Boolean = true,
    )

    private const val SYSTEM = """你是「本地画像」引擎。输入是一组统计：设备信息、类目分布、与普通用户的占比倍率、典型年龄结构、代表性应用、规则信号。

严格按下面格式输出，不要多写别的内容：

【总览】一句话点出这个人最突出的特征
【维度】年龄段=25-34；消费=品质；技术=开发者级；重心=事业
【判断】一句结论
【依据】只引用输入里出现过的类目名或应用名
【把握】高
【判断】第二句结论
【依据】同上
【把握】中
【边界】以上是根据已安装应用的统计推测，不涉及任何账号与个人内容。

固定选项（必须从中选一个）：
年龄段：<18 / 18-24 / 25-34 / 35-44 / 45+
消费：节俭 / 均衡 / 品质 / 高端
技术：普通 / 熟练 / 开发者级
重心：学业 / 事业 / 家庭 / 兴趣 / 健康

规则：
1. 【依据】必须来自输入，找不到依据的判断不要写。
2. 优先看倍率而不是绝对数量，3 倍以上才算特征。
3. 绝不编造姓名、地址、公司、职业、账号。
4. 不写「游戏多所以是男性」这类没有依据的刻板推断。
5. 全文不超过 220 字。

示例：
输入：第三方应用 60 个；类目 游戏 5、工具 8、社交 6；倍率均低于 2 倍。
输出：
【总览】一个装机偏少、以工具和社交为主的普通上班族。
【维度】年龄段=25-34；消费=均衡；技术=普通；重心=事业
【判断】装机量偏少，对新应用兴趣不高
【依据】第三方应用 60 个，没有高于平均 2 倍的类目
【把握】中
【边界】以上是根据已安装应用的统计推测，不涉及任何账号与个人内容。"""

    fun build(seed: AnalysisSeed, deviceNote: String? = null, thinking: Boolean = false): String {
        val user = buildString {
            append(seed.promptBlock(deviceNote))
            append("\n请开始分析。")
        }
        return buildString {
            append(IM_START).append("system\n").append(SYSTEM).append(IM_END).append('\n')
            append(IM_START).append("user\n").append(user).append(IM_END).append('\n')
            append(IM_START).append("assistant\n")
            if (!thinking) append("<think>\n\n</think>\n\n")
        }
    }

    fun overview(text: String): String {
        val m = Regex("【总览】(.*?)(?=【|$)", setOf(RegexOption.DOT_MATCHES_ALL)).find(text)
        return m?.groupValues?.get(1)?.trim()?.take(120) ?: ""
    }

    fun dimensions(text: String): List<Pair<String, String>> {
        val m = Regex("【维度】(.*?)(?=【|$)", setOf(RegexOption.DOT_MATCHES_ALL)).find(text) ?: return emptyList()
        return m.groupValues[1].split('；', ';', '|')
            .mapNotNull { seg ->
                val idx = seg.indexOf('=')
                if (idx <= 0) null else seg.substring(0, idx).trim() to seg.substring(idx + 1).trim()
            }
            .filter { it.first.isNotBlank() && it.second.isNotBlank() }
            .take(6)
    }

    private val claimRegex = Regex(
        "【判断】(.*?)【依据】(.*?)【把握】(.*?)(?=【判断】|【边界】|$)",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun claims(text: String): List<Claim> =
        claimRegex.findAll(text).map {
            Claim(
                judgment = it.groupValues[1].trim().take(80),
                evidence = it.groupValues[2].trim().take(120),
                confidence = it.groupValues[3].trim().take(4),
            )
        }.filter { it.judgment.isNotBlank() }.take(5).toList()

    fun boundary(text: String): String {
        val m = Regex("【边界】(.*?)$", setOf(RegexOption.DOT_MATCHES_ALL)).find(text)
        return m?.groupValues?.get(1)?.trim()?.take(120) ?: ""
    }

    /** 把流式输出拆成「思考」和「正文」。 */
    fun split(raw: String): Pair<String, String> {
        val close = raw.indexOf("</think>")
        if (close >= 0) {
            val t = raw.substring(0, close).removePrefix("<think>").trim()
            val a = raw.substring(close + "</think>".length).trimStart('\n', ' ')
            return t to a
        }
        if (raw.startsWith("<think>")) {
            return raw.removePrefix("<think>").trim() to ""
        }
        return "" to raw
    }
}
