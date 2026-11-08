package com.localpersona.app.llm

import com.localpersona.app.data.AnalysisSeed

/**
 * Qwen3.5 的对话格式。不用 jinja 模板，手工拼：
 * - 关闭 thinking（非思考模式会在 assistant 前缀后给一个空的 <think></think>）
 * - system 只讲规则，user 放种子数据
 */
object PromptBuilder {

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    private const val SYSTEM = """你是「本地画像」引擎。你会收到一组统计：设备型号、系统版本、已安装应用的类目分布、若干规则信号，可能还有一条来自公开检索的机型定位。

据此推测这个使用者是什么样的人。要求：
1. 只做合理推测，绝不编造姓名、地址、公司、账号。
2. 结论具体、有区分度，不要写「热爱生活」「积极向上」这类空话。
3. 不要复述输入的数字，直接给判断。
4. 简体中文，严格按四段输出，每段一行标题加一到三句话：
【一句话判断】
【生活方式】
【消费与技术】
【边界】
5. 【边界】只写一句：以上是根据已安装应用的统计推测，不涉及任何账号与个人内容。
全文不超过 300 字。"""

    fun build(seed: AnalysisSeed, deviceNote: String? = null): String {
        val user = buildString {
            append(seed.promptBlock(deviceNote))
            append("\n请开始分析。")
        }
        return buildString {
            append(IM_START).append("system\n").append(SYSTEM).append(IM_END).append('\n')
            append(IM_START).append("user\n").append(user).append(IM_END).append('\n')
            append(IM_START).append("assistant\n")
            append("<think>\n\n</think>\n\n")
        }
    }

    /** 去掉模型可能自己吐出来的 think 段。 */
    fun stripThinking(text: String): String {
        val close = text.indexOf("</think>")
        if (close >= 0) {
            val head = text.substring(0, close)
            if (head.contains("<think>") && head.indexOf("<think>") <= 2) {
                return text.substring(close + "</think>".length).trimStart('\n', ' ')
            }
        }
        return text
    }
}
