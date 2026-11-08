package com.localpersona.app.llm

/**
 * llama.cpp 的 JNI 桥。native 侧常驻模型，重复调用只重建上下文，
 * 避免每次分析都重新加载 500MB。
 */
object LlamaBridge {

    /** 返回 false 表示请求中止生成。 */
    fun interface TokenSink {
        fun onToken(text: String): Boolean
    }

    init {
        System.loadLibrary("persona")
    }

    external fun nativeInit()
    external fun nativeLoadModel(path: String): Int
    external fun nativeFreeModel()
    external fun nativeIsLoaded(): Boolean
    external fun nativeSystemInfo(): String

    external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        minP: Float,
        threads: Int,
        nCtx: Int,
        sink: TokenSink,
    ): Int

    fun systemInfo(): String = runCatching { nativeSystemInfo() }.getOrDefault("unavailable")
}
