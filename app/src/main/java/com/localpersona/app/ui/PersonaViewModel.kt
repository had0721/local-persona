package com.localpersona.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    val deviceHeadline: String = "",
    val deviceDetail: String = "",
    val seedSummary: String = "",
    val hitRate: String = "",
    val appsCount: Int = 0,
    val streamed: String = "",
    val progress: Float = 0f,
    val transferText: String = "",
    val error: String? = null,
    val elapsedMs: Long = 0,
    val tokensPerSec: Float = 0f,
    val modelReady: Boolean = false,
    val modelLoaded: Boolean = false,
    val showSeed: Boolean = false,
    val searchEnabled: Boolean = true,
    val enrichText: String = "",
    val seedText: String = "",
    val nativeInfo: String = "",
) {
    val busy: Boolean
        get() = phase == Phase.SCANNING || phase == Phase.DOWNLOADING ||
            phase == Phase.LOADING || phase == Phase.GENERATING

    val step: Int
        get() = when (phase) {
            Phase.IDLE -> 0
            Phase.SCANNING -> 1
            Phase.DOWNLOADING, Phase.LOADING -> 2
            Phase.GENERATING, Phase.DONE -> 3
            Phase.ERROR -> 0
        }
}

class PersonaViewModel(app: Application) : AndroidViewModel(app) {

    private val settings = Settings(app)
    private val _state = MutableStateFlow(
        UiState(
            modelReady = ModelManager.isReady(app),
            showSeed = settings.showSeed,
            searchEnabled = settings.searchEnabled,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null
    private var warmJob: Job? = null

    @Volatile
    private var cancelRequested = false

    init {
        runCatching { LlamaBridge.nativeInit() }
        // 预热：模型已下载时，进 App 就开始读进内存。
        // 这样用户第一次点「开始」时，加载那 2~5 秒已经被藏掉了。
        if (_state.value.modelReady) warmUp()
    }

    fun warmUp() {
        if (warmJob?.isActive == true || LlamaBridge.nativeIsLoaded()) return
        warmJob = viewModelScope.launch(Dispatchers.Default) {
            val ctx = getApplication<Application>()
            if (!ModelManager.isReady(ctx)) return@launch
            val path = ModelManager.modelFile(ctx).absolutePath
            val rc = runCatching { LlamaBridge.nativeLoadModel(path) }.getOrDefault(-1)
            if (rc == 0) {
                _state.value = _state.value.copy(modelLoaded = true, nativeInfo = LlamaBridge.systemInfo())
            }
        }
    }

    fun refreshModelState() {
        _state.value = _state.value.copy(modelReady = ModelManager.isReady(getApplication()))
    }

    fun setShowSeed(v: Boolean) {
        settings.showSeed = v
        _state.value = _state.value.copy(showSeed = v)
    }

    fun setSearchEnabled(v: Boolean) {
        settings.searchEnabled = v
        _state.value = _state.value.copy(searchEnabled = v)
    }

    fun cancel() {
        cancelRequested = true
        job?.cancel()
        job = null
        _state.value = _state.value.copy(phase = Phase.IDLE, status = "已停止", progress = 0f)
    }

    fun deleteModel() {
        ModelManager.delete(getApplication())
        runCatching { LlamaBridge.nativeFreeModel() }
        _state.value = _state.value.copy(
            modelReady = false,
            modelLoaded = false,
            status = "模型已删除",
        )
    }

    fun downloadModelOnly() {
        if (job?.isActive == true) return
        cancelRequested = false
        job = viewModelScope.launch {
            try {
                downloadModel()
                _state.value = _state.value.copy(phase = Phase.IDLE, status = "模型已就绪")
                warmUp()
            } catch (t: Throwable) {
                _state.value = _state.value.copy(phase = Phase.ERROR, error = humanize(t), status = "下载失败")
            }
        }
    }

    fun startAnalysis() {
        if (job?.isActive == true) return
        cancelRequested = false
        val started = System.currentTimeMillis()

        job = viewModelScope.launch {
            try {
                _state.value = _state.value.copy(
                    phase = Phase.SCANNING,
                    status = "正在读取已安装应用…",
                    streamed = "",
                    error = null,
                    tokensPerSec = 0f,
                    progress = 0f,
                )

                val ctx = getApplication<Application>()
                // 三件事都是毫秒级，合在后台一次做完
                val (map, apps, facts) = withContext(Dispatchers.Default) {
                    val m = CategoryMap.load(ctx)
                    val a = AppScanner.scan(ctx)
                    val f = DeviceFacts.read(ctx)
                    Triple(m, a, f)
                }
                val seed = FeaturePacker.pack(apps, map, facts)

                _state.value = _state.value.copy(
                    deviceHeadline = "${facts.manufacturer} ${facts.model}".trim(),
                    deviceDetail = buildString {
                        append("Android ${facts.androidRelease}")
                        append(" · ").append(facts.abis.firstOrNull() ?: "arm64-v8a")
                        append(" · ").append(facts.cores).append(" 核")
                        append(" · ").append("${"%.0f".format(Locale.US, facts.ramGb)} GB")
                        append(" · ").append(if (facts.languageTag.startsWith("zh")) "中文" else facts.languageTag)
                    },
                    appsCount = seed.thirdPartyCount,
                    seedSummary = seed.summary(),
                    hitRate = FeaturePacker.formatPercent(FeaturePacker.hitRate(seed)),
                    seedText = seed.promptBlock(),
                )

                if (!ModelManager.isReady(ctx)) {
                    downloadModel()
                }

                // 联网识别与模型加载并行：网络在等，CPU 在加载，两件事同时发生
                val resolver = SearchResolver(SearchCache(ctx))
                val enrichJob = if (settings.searchEnabled) {
                    async {
                        runCatching { resolver.enrich(seed.unmappedPackages, facts.model) }
                            .getOrDefault(Enrichment())
                    }
                } else {
                    null
                }

                _state.value = _state.value.copy(
                    phase = Phase.LOADING,
                    status = if (seed.unmappedPackages.isEmpty()) {
                        "正在加载模型…"
                    } else {
                        "加载模型，同时识别 ${seed.unmappedPackages.size} 个未知应用…"
                    },
                )

                loadModel()
                val enrich = enrichJob?.await() ?: Enrichment()
                val finalSeed = FeaturePacker.merge(seed, enrich.categories)

                _state.value = _state.value.copy(
                    seedText = finalSeed.promptBlock(enrich.deviceNote),
                    seedSummary = finalSeed.summary(),
                    hitRate = FeaturePacker.formatPercent(FeaturePacker.hitRate(finalSeed)),
                    enrichText = when {
                        !settings.searchEnabled -> "联网已关闭 · 纯本地识别"
                        enrich.fetched == 0 && enrich.fromCache == 0 && enrich.deviceNote == null ->
                            "本地已全部识别 · 未联网"
                        else -> buildString {
                            append("联网识别 ")
                            append(enrich.fetched + enrich.fromCache)
                            append(" 项 · ")
                            if (enrich.fetched > 0) append("${enrich.elapsedMs} ms") else append("走缓存")
                        }
                    },
                )

                generate(finalSeed, enrich.deviceNote, started)
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

    private suspend fun downloadModel() {
        val ctx = getApplication<Application>()
        _state.value = _state.value.copy(
            phase = Phase.DOWNLOADING,
            status = "首次使用，正在下载模型",
            progress = 0f,
        )
        ModelManager.download(ctx, onProgress = { p ->
            val mb = p.downloaded / 1024.0 / 1024.0
            val total = p.total / 1024.0 / 1024.0
            _state.value = _state.value.copy(
                progress = p.fraction,
                transferText = String.format(Locale.US, "%.0f / %.0f MB", mb, total),
            )
        })
        _state.value = _state.value.copy(modelReady = true, progress = 0f, transferText = "")
    }

    private suspend fun loadModel() {
        if (LlamaBridge.nativeIsLoaded()) {
            _state.value = _state.value.copy(phase = Phase.LOADING, status = "模型已在内存中")
            return
        }
        _state.value = _state.value.copy(phase = Phase.LOADING, status = "正在把模型读进内存…")
        val ctx = getApplication<Application>()
        val path = ModelManager.modelFile(ctx).absolutePath
        val rc = withContext(Dispatchers.Default) { LlamaBridge.nativeLoadModel(path) }
        if (rc != 0) throw IllegalStateException("模型加载失败（code=$rc）")
        _state.value = _state.value.copy(
            modelLoaded = true,
            nativeInfo = LlamaBridge.systemInfo(),
        )
    }

    private suspend fun generate(seed: AnalysisSeed, deviceNote: String?, started: Long) {
        val prompt = PromptBuilder.build(seed, deviceNote)
        val maxTokens = settings.maxTokens
        val threads = if (settings.threads > 0) {
            settings.threads
        } else {
            val cores = Runtime.getRuntime().availableProcessors()
            max(2, min(4, cores - 2))
        }
        val nCtx = 2048

        _state.value = _state.value.copy(
            phase = Phase.GENERATING,
            status = "正在生成画像…",
            progress = 0f,
        )

        val builder = StringBuilder()
        var firstTokenAt = 0L
        var lastPublish = 0L

        val sink = LlamaBridge.TokenSink { piece ->
            if (cancelRequested) {
                false
            } else {
                builder.append(piece)
                val now = System.currentTimeMillis()
                if (firstTokenAt == 0L) {
                    firstTokenAt = now
                    _state.value = _state.value.copy(elapsedMs = now - started)
                }
                if (now - lastPublish > 66) {
                    lastPublish = now
                    _state.value = _state.value.copy(
                        streamed = PromptBuilder.stripThinking(builder.toString()),
                    )
                }
                true
            }
        }

        val rc = withContext(Dispatchers.Default) {
            LlamaBridge.nativeGenerate(
                prompt = prompt,
                maxTokens = maxTokens,
                temperature = 0.35f,
                topP = 0.9f,
                topK = 40,
                minP = 0.05f,
                threads = threads,
                nCtx = nCtx,
                sink = sink,
            )
        }

        val finished = System.currentTimeMillis()
        val finalText = PromptBuilder.stripThinking(builder.toString())
        if (rc != 0 && finalText.isBlank()) {
            _state.value = _state.value.copy(
                phase = Phase.ERROR,
                error = "生成失败（code=$rc）",
                status = "生成失败",
            )
            return
        }

        val genMs = if (firstTokenAt > 0) finished - firstTokenAt else finished - started
        val approxTokens = finalText.length / 1.6f
        val tps = if (genMs > 0) approxTokens / (genMs / 1000f) else 0f

        _state.value = _state.value.copy(
            phase = Phase.DONE,
            status = "完成",
            streamed = finalText,
            elapsedMs = finished - started,
            tokensPerSec = tps,
        )
    }

    private fun humanize(t: Throwable): String {
        val m = t.message
        return when {
            m == null -> t.javaClass.simpleName
            m.contains("ENOSPC") -> "存储空间不足"
            m.contains("HTTP 4") -> "模型下载被拒绝（$m）"
            m.contains("Unable to resolve host") || m.contains("timeout") -> "网络不可用，可稍后重试下载"
            else -> m
        }
    }
}
