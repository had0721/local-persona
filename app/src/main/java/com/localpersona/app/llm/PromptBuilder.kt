package com.localpersona.app.llm

import com.localpersona.app.data.AnalysisSeed

/**
 * Qwen3.5 的对话格式。不用 jinja 模板，手工拼。
 *
 * thinking 关闭时，assistant 前缀后面直接给一个空的 <think></think>，省 2~3 倍 token；
 * 打开时留空，让模型自己生成思考段。
 */
object PromptBuilder {

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    private const val SYSTEM = """你是「本地画像」引擎。你会收到一组统计：设备型号、系统版本、已安装应用的类目分布、若干规则信号，可能还有一条来自公开检索的机型定位。

据此推测这个使用者是什么样的人。输出格式必须严格照下面来：

【总览】一句话说这个人最突出的特征。

【判断】一句话结论
【依据】只能引用输入里出现过的类目名或信号
【把握】高 或 中 或 低

（上面这三行重复 3 次，共 3 条判断）

【边界】以上是根据已安装应用的统计推测，不涉及任何账号与个人内容。

硬性规则：
1. 【依据】必须来自输入，输入里没有的一律不许写；找不到依据的判断就不要写。
2. 绝不编造姓名、地址、公司、职业、账号。
3. 不要写「游戏多所以是男性」这类没有依据的刻板推断。
4. 不复述数字，直接给判断。
5. 全文不超过 260 字。"""

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

    data class Claim(val judgment: String, val evidence: String, val confidence: String)

    private val claimRegex = Regex(
        "【判断】\\s*(.+?)\\s*【依据】\\s*(.+?)\\s*【把握】\\s*(.+?)(?=【判断】|【边界】|$)",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )

    fun parseClaims(text: String): List<Claim> =
        claimRegex.findAll(text).map {
            Claim(
                judgment = it.groupValues[1].trim().take(80),
                evidence = it.groupValues[2].trim().take(120),
                confidence = it.groupValues[3].trim().take(4),
            )
        }.filter { it.judgment.isNotBlank() }.take(6).toList()

    fun overview(text: String): String {
        val m = Regex("【总览】\\s*(.+?)(?=【|$)", setOf(RegexOption.DOT_MATCHES_ALL)).find(text)
        return m?.groupValues?.get(1)?.trim()?.take(120) ?: ""
    }

    fun boundary(text: String): String {
        val m = Regex("【边界】\\s*(.+?)$", setOf(RegexOption.DOT_MATCHES_ALL)).find(text)
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
