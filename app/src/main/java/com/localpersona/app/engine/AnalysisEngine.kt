package com.localpersona.app.engine

import android.content.Context
import com.localpersona.app.data.AnalysisSeed
import com.localpersona.app.data.AppScanner
import com.localpersona.app.data.CategoryMap
import com.localpersona.app.data.DeviceFacts
import com.localpersona.app.data.FeaturePacker
import com.localpersona.app.data.Settings
import com.localpersona.app.llm.LlamaBridge
import com.localpersona.app.llm.ModelManager
import com.localpersona.app.llm.PromptBuilder
import com.localpersona.app.net.Enrichment
import com.localpersona.app.net.SearchCache
import com.localpersona.app.net.SearchResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

enum class Phase { IDLE, SCANNING, DOWNLOADING, LOADING, GENERATING, DONE, ERROR }

data class UiState(
    val phase: Phase = Phase.IDLE,
    val status: String = "点击开始，全部在本机完成",
    val progress: Float = 0f,
    val deviceHeadline: String = "",
    val deviceDetail: String = "",
    val seedSummary: String = "",
    val hitRate: String = "",
    val appsCount: Int = 0,
    val thinking: String = "",
    val answer: String = "",
    val enrichText: String = "",
    val transferText: String = "",
    val error: String? = null,
    val elapsedMs: Long = 0,
    val tokensPerSec: Float = 0f,
    val modelReady: Boolean = false,
    val showThinking: Boolean = false,
    val showSeed: Boolean = false,
    val overlayEnabled: Boolean = true,
    val seedText: String = "",
) {
    val busy: Boolean
        get() = phase == Phase.SCANNING || phase == Phase.DOWNLOADING ||
            phase == Phase.LOADING || phase == Phase.GENERATING

    val finished: Boolean
        get() = phase == Phase.DONE || phase == Phase.ERROR

    val step: Int
        get() = when (phase) {
            Phase.IDLE -> 0
            Phase.SCANNING -> 1
            Phase.DOWNLOADING, Phase.LOADING -> 2
            Phase.GENERATING, Phase.DONE -> 3
            Phase.ERROR -> 0
        }
}

/**
 * 全局单例：分析的执行与状态都在这里，和 Activity 生命周期无关。
 * 前台服务持有它，所以切后台、锁屏、Activity 重建都不会中断推理。
 */
object AnalysisEngine {

    private var app: Context? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var cancelRequested = false

    private fun settings(): Settings? = app?.let { Settings(it) }

    fun attach(ctx: Context) {
        if (app != null) return
        app = ctx.applicationContext
        runCatching { LlamaBridge.nativeInit() }
        val s = Settings(app!!)
        _state.value = _state.value.copy(
            modelReady = ModelManager.isReady(app!!),
            showThinking = s.showThinking,
            showSeed = s.showSeed,
            overlayEnabled = s.overlayEnabled,
        )
        if (_state.value.modelReady) warmUp()
    }

    fun setShowThinking(v: Boolean) {
        settings()?.showThinking = v
        _state.value = _state.value.copy(showThinking = v)
    }

    fun setShowSeed(v: Boolean) {
        settings()?.showSeed = v
        _state.value = _state.value.copy(showSeed = v)
    }

    fun setOverlayEnabled(v: Boolean) {
        settings()?.overlayEnabled = v
        _state.value = _state.value.copy(overlayEnabled = v)
    }

    val isRunning: Boolean get() = job?.isActive == true

    fun warmUp() {
        val ctx = app ?: return
        if (LlamaBridge.nativeIsLoaded()) return
        scope.launch {
            if (!ModelManager.isReady(ctx)) return@launch
            val path = ModelManager.modelFile(ctx).absolutePath
            runCatching { LlamaBridge.nativeLoadModel(path) }
        }
    }

    fun cancel() {
        cancelRequested = true
        job?.cancel()
        job = null
        _state.value = _state.value.copy(phase = Phase.IDLE, status = "已停止", progress = 0f)
    }

    fun deleteModel() {
        val ctx = app ?: return
        ModelManager.delete(ctx)
        runCatching { LlamaBridge.nativeFreeModel() }
        _state.value = _state.value.copy(modelReady = false, status = "模型已删除")
    }

    fun downloadOnly() {
        val ctx = app ?: return
        if (isRunning) return
        cancelRequested = false
        job = scope.launch {
            try {
                downloadModel(ctx)
                _state.value = _state.value.copy(phase = Phase.IDLE, status = "模型已就绪", progress = 0f)
                warmUp()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(phase = Phase.ERROR, error = humanize(t), status = "下载失败")
            }
        }
    }

    fun start() {
        val ctx = app ?: return
        if (isRunning) return
        cancelRequested = false
        val started = System.currentTimeMillis()

        job = scope.launch {
            try {
                _state.value = _state.value.copy(
                    phase = Phase.SCANNING,
                    status = "正在读取已安装应用…",
                    progress = 0.03f,
                    answer = "",
                    thinking = "",
                    error = null,
                    tokensPerSec = 0f,
                )

                val (map, apps, facts) = withContext(Dispatchers.Default) {
                    val m = CategoryMap.load(ctx)
                    val a = AppScanner.scan(ctx)
                    val f = DeviceFacts.read(ctx)
                    Triple(m, a, f)
                }
                val seed = FeaturePacker.pack(apps, map, facts)

                _state.value = _state.value.copy(
                    progress = 0.08f,
                    deviceHeadline = "${facts.manufacturer} ${facts.model}".trim(),
                    deviceDetail = deviceLine(facts),
                    appsCount = seed.thirdPartyCount,
                    seedSummary = seed.summary(),
                    hitRate = FeaturePacker.formatPercent(FeaturePacker.hitRate(seed)),
                    seedText = seed.promptBlock(),
                )

                if (!ModelManager.isReady(ctx)) {
                    downloadModel(ctx)
                }

                val resolver = SearchResolver(SearchCache(ctx))
                val enrichJob = if (Settings(ctx).searchEnabled) {
                    async {
                        runCatching { resolver.enrich(seed.unmappedPackages, facts.model) }
                            .getOrDefault(Enrichment())
                    }
                } else {
                    null
                }

                _state.value = _state.value.copy(
                    phase = Phase.LOADING,
                    progress = 0.62f,
                    status = if (seed.unmappedPackages.isEmpty()) {
                        "正在加载模型…"
                    } else {
                        "加载模型，同时识别 ${seed.unmappedPackages.size} 个未知应用…"
                    },
                )

                loadModel(ctx)
                val enrich = enrichJob?.await() ?: Enrichment()
                val finalSeed = FeaturePacker.merge(seed, enrich.categories)

                _state.value = _state.value.copy(
                    progress = 0.68f,
                    seedText = finalSeed.promptBlock(enrich.deviceNote),
                    seedSummary = finalSeed.summary(),
                    hitRate = FeaturePacker.formatPercent(FeaturePacker.hitRate(finalSeed)),
                    enrichText = enrichLine(enrich),
                )

                generate(ctx, finalSeed, enrich.deviceNote, started)
            } catch (t: Throwable) {
                if (cancelRequested) return@launch
                _state.value = _state.value.copy(
                    phase = Phase.ERROR,
                    status = "出错了",
                    error = humanize(t),
                )
            }
        }
    }

    private fun deviceLine(facts: DeviceFacts): String = buildString {
        append("Android ${facts.androidRelease}")
        append(" · ").append(facts.abis.firstOrNull() ?: "arm64-v8a")
        append(" · ").append(facts.cores).append(" 核")
        append(" · ").append(String.format(Locale.US, "%.0f GB", facts.ramGb))
        append(" · ").append(if (facts.languageTag.startsWith("zh")) "中文" else facts.languageTag)
    }

    private fun enrichLine(enrich: Enrichment): String = when {
        enrich.fetched == 0 && enrich.fromCache == 0 && enrich.deviceNote == null -> "本地已全部识别 · 未联网"
        else -> buildString {
            append("联网识别 ").append(enrich.fetched + enrich.fromCache).append(" 项 · ")
            if (enrich.fetched > 0) append("${enrich.elapsedMs} ms") else append("走缓存")
        }
    }

    private suspend fun downloadModel(ctx: Context) {
        _state.value = _state.value.copy(
            phase = Phase.DOWNLOADING,
            status = "首次使用，正在下载模型",
            progress = 0.08f,
        )
        ModelManager.download(ctx, onProgress = { p ->
            val mb = p.downloaded / 1024.0 / 1024.0
            val total = p.total / 1024.0 / 1024.0
            _state.value = _state.value.copy(
                progress = 0.08f + p.fraction * 0.52f,
                transferText = String.format(Locale.US, "%.0f / %.0f MB", mb, total),
            )
        })
        _state.value = _state.value.copy(modelReady = true, transferText = "")
    }

    private suspend fun loadModel(ctx: Context) {
        if (LlamaBridge.nativeIsLoaded()) return
        val path = ModelManager.modelFile(ctx).absolutePath
        val rc = withContext(Dispatchers.Default) { LlamaBridge.nativeLoadModel(path) }
        if (rc != 0) throw IllegalStateException("模型加载失败（code=$rc）")
        _state.value = _state.value.copy(modelReady = true)
    }

    private suspend fun generate(ctx: Context, seed: AnalysisSeed, deviceNote: String?, started: Long) {
        val s = Settings(ctx)
        val thinking = s.showThinking
        val prompt = PromptBuilder.build(seed, deviceNote, thinking)
        val maxTokens = if (thinking) max(s.maxTokens, 640) else s.maxTokens
        val threads = if (s.threads > 0) {
            s.threads
        } else {
            val cores = Runtime.getRuntime().availableProcessors()
            max(2, min(4, cores - 2))
        }

        _state.value = _state.value.copy(
            phase = Phase.GENERATING,
            status = if (thinking) "正在思考并生成…" else "正在生成画像…",
            progress = 0.7f,
        )

        val raw = StringBuilder()
        var firstChunk = 0L
        var lastPublish = 0L

        val sink = LlamaBridge.TokenSink { piece ->
            if (cancelRequested) {
                false
            } else {
                raw.append(piece)
                val now = System.currentTimeMillis()
                if (firstChunk == 0L) firstChunk = now
                if (now - lastPublish > 66) {
                    lastPublish = now
                    val (t, a) = PromptBuilder.split(raw.toString())
                    val frac = (raw.length / 3.0f).coerceAtMost(220f) / 220f
                    _state.value = _state.value.copy(
                        thinking = t,
                        answer = a,
                        progress = 0.7f + frac * 0.28f,
                        elapsedMs = now - started,
                    )
                }
                true
            }
        }

        val rc = withContext(Dispatchers.Default) {
            LlamaBridge.nativeGenerate(
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = 0.25f,
                topP = 0.9f,
                topK = 40,
                minP = 0.05f,
                threads = threads,
                nCtx = 2048,
                sink = sink,
            )
        }

        val finished = System.currentTimeMillis()
        val (t, a) = PromptBuilder.split(raw.toString())
        if (rc != 0 && a.isBlank()) {
            _state.value = _state.value.copy(phase = Phase.ERROR, error = "生成失败（code=$rc）", status = "生成失败")
            return
        }
        val genMs = if (firstChunk > 0) finished - firstChunk else finished - started
        val approxTokens = a.length / 1.6f
        _state.value = _state.value.copy(
            phase = Phase.DONE,
            status = "完成",
            thinking = t,
            answer = a,
            progress = 1f,
            elapsedMs = finished - started,
            tokensPerSec = if (genMs > 0) approxTokens / (genMs / 1000f) else 0f,
        )
    }

    private fun humanize(t: Throwable): String {
        val m = t.message
        return when {
            m == null -> t.javaClass.simpleName
            m.contains("ENOSPC") -> "存储空间不足"
            m.contains("Unable to resolve host") || m.contains("timeout") -> "网络不可用，可稍后重试下载"
            else -> m
        }
    }
}
