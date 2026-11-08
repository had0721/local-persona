package com.localpersona.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.localpersona.app.data.Settings as AppSettings
import com.localpersona.app.engine.AnalysisEngine
import com.localpersona.app.engine.Phase
import com.localpersona.app.engine.UiState
import com.localpersona.app.llm.ModelManager
import com.localpersona.app.llm.PromptBuilder
import com.localpersona.app.service.AnalysisService
import com.localpersona.app.ui.Accent
import com.localpersona.app.ui.Mono
import com.localpersona.app.ui.PersonaTheme
import com.localpersona.app.update.ApkInstaller
import com.localpersona.app.update.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonaTheme {
                PersonaScreen(this)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaScreen(activity: ComponentActivity) {
    val state by AnalysisEngine.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showThinkingBody by remember { mutableStateOf(false) }
    val update = remember { UpdateState(activity) }

    LaunchedEffect(Unit) {
        if (update.autoUpdate) checkForUpdate(activity, update, install = false)
    }

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    fun startAnalysis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        AnalysisService.start(activity)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text("本地画像", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "ON-DEVICE · 零权限读数 · 不联网 · 后台可运行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DeviceStrip(state)
            UpdateBanner(update, activity)
            MetricRow(state)
            if (state.enrichText.isNotBlank()) EnrichLine(state.enrichText)
            StepLine(state.step)
            ActionButton(state) {
                when {
                    state.busy -> AnalysisService.cancel(activity)
                    !state.modelReady -> AnalysisService.download(activity)
                    else -> startAnalysis()
                }
            }
            if (state.busy || state.finished) ProgressCard(state)
            ThinkingCard(state, showThinkingBody) { showThinkingBody = it }
            ErrorBlock(state.error)
            ResultCard(state)
            if (state.showSeed && state.seedText.isNotBlank()) SeedBlock(state.seedText)
            Footer(state)
        }
    }

    if (showSettings) {
        SettingsDialog(state, activity, update) { showSettings = false }
    }
}

@Composable
private fun DeviceStrip(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (state.deviceHeadline.isBlank()) "读取设备信息…" else state.deviceHeadline,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            state.deviceDetail.ifBlank { "机型、系统、语言全部来自 Build，不需要权限" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.seedSummary.isNotBlank()) {
            Text(
                state.seedSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricRow(state: UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Metric("已装应用", if (state.appsCount > 0) "${state.appsCount}" else "—", Modifier.weight(1f))
        Metric("类目命中", state.hitRate.ifBlank { "—" }, Modifier.weight(1f))
        Metric(
            "总耗时",
            if (state.elapsedMs > 0) String.format(Locale.US, "%.1f s", state.elapsedMs / 1000.0) else "—",
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun EnrichLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(5.dp),
        ) {}
        Spacer(Modifier.width(7.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                fontFamily = Mono,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StepLine(step: Int) {
    val labels = listOf("采集", "推理", "输出")
    Row(verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val active = step >= i + 1
            Surface(
                shape = RoundedCornerShape(50),
                color = if (active) Accent else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(6.dp),
            ) {}
            Spacer(Modifier.width(6.dp))
            Text(
                "0${i + 1} $label",
                style = MaterialTheme.typography.labelSmall,
                color = if (active) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (i < labels.lastIndex) {
                Spacer(Modifier.width(10.dp))
                HorizontalDivider(
                    modifier = Modifier.width(18.dp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun ActionButton(state: UiState, onClick: () -> Unit) {
    val label = when {
        state.busy -> "停止"
        !state.modelReady -> "下载模型（约 508 MB）"
        else -> "开始分析"
    }
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(50.dp),
        shape = RoundedCornerShape(10.dp),
        colors = if (state.busy) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ButtonDefaults.buttonColors(containerColor = Accent)
        },
    ) {
        Icon(
            if (state.busy) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProgressCard(state: UiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (state.phase) {
                        Phase.DONE -> "已完成"
                        Phase.ERROR -> "已中断"
                        else -> "进行中"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (state.phase == Phase.DONE) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = Mono,
                    color = Accent,
                )
            }
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = Accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    state.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (state.transferText.isNotBlank()) {
                    Text(
                        state.transferText,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (state.busy && state.overlayEnabled) {
                Text(
                    "悬浮窗可以拖到任意位置，— 收起，× 关掉（关掉后仍在后台继续）",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThinkingCard(state: UiState, expanded: Boolean, onToggle: (Boolean) -> Unit) {
    if (!state.showThinking) return
    if (state.thinking.isBlank() && state.phase != Phase.GENERATING) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (state.phase == Phase.GENERATING && state.answer.isBlank()) "正在思考…" else "推理过程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onToggle(!expanded) }) {
                    Text(if (expanded) "收起" else "展开", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (expanded) {
                SelectionContainer {
                    Text(
                        state.thinking.ifBlank { "…" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorBlock(error: String?) {
    if (error.isNullOrBlank()) return
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Text(
            error,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ResultCard(state: UiState) {
    if (state.answer.isBlank() && state.phase != Phase.GENERATING) return
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "画像结果",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (state.phase == Phase.GENERATING) {
                    Text("生成中", style = MaterialTheme.typography.labelSmall, color = Accent)
                } else if (state.tokensPerSec > 0f) {
                    Text(
                        String.format(Locale.US, "%.1f tok/s", state.tokensPerSec),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            val claims = if (state.phase == Phase.GENERATING) emptyList() else PromptBuilder.parseClaims(state.answer)
            if (claims.isEmpty()) {
                SelectionContainer {
                    Text(
                        state.answer.ifBlank { "…" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else {
                val lead = PromptBuilder.overview(state.answer)
                if (lead.isNotBlank()) {
                    Text(
                        lead,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                claims.forEach { claim -> ClaimRow(claim) }
                val edge = PromptBuilder.boundary(state.answer)
                if (edge.isNotBlank()) {
                    Text(
                        edge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaimRow(claim: PromptBuilder.Claim) {
    val tone = when (claim.confidence.take(1)) {
        "高" -> Accent
        "中" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    claim.judgment,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    claim.confidence.ifBlank { "—" },
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                )
            }
            Text(
                "依据：${claim.evidence}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SeedBlock(seed: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "送进模型的全部内容",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(
                    seed,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = Mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Footer(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.modelReady) "模型已就绪 · 推理全部在 CPU 上本地完成" else "首次使用需下载约 508 MB 模型",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "读取的只有包名与机型，不含聊天、照片、位置或账号。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsDialog(
    state: UiState,
    activity: ComponentActivity,
    update: UpdateState,
    onDismiss: () -> Unit,
) {
    fun openOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${activity.packageName}"),
        )
        runCatching { activity.startActivity(intent) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ToggleRow(
                    title = "悬浮窗进度条",
                    desc = "叠在别的应用上层，可拖动、可收起、可关掉。需要「显示在其他应用上层」权限",
                    checked = state.overlayEnabled,
                ) { on ->
                    AnalysisEngine.setOverlayEnabled(on)
                    if (on && !Settings.canDrawOverlays(activity)) openOverlayPermission()
                }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                ToggleRow(
                    title = "显示推理过程",
                    desc = "把模型的 thinking 段也显示出来。会多花 2~3 倍 token，明显更慢",
                    checked = state.showThinking,
                ) { on -> AnalysisEngine.setShowThinking(on) }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                ToggleRow(
                    title = "显示送给模型的内容",
                    desc = "方便核对“它到底看到了什么”",
                    checked = state.showSeed,
                ) { on -> AnalysisEngine.setShowSeed(on) }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("模型", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        ModelManager.DEFAULT.label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (state.modelReady) "已下载" else "未下载",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = Mono,
                        color = if (state.modelReady) Accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            AnalysisService.download(activity)
                            onDismiss()
                        }) { Text("下载") }
                        TextButton(onClick = {
                            AnalysisEngine.deleteModel()
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("删除")
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                UpdateSection(activity, update)

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                Text(
                    "本地画像 v0.2 · llama.cpp + Qwen3.5-0.8B",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private class UpdateState(ctx: Context) {
    private val settings = AppSettings(ctx)
    var autoUpdate by mutableStateOf(settings.autoUpdate)
    var mirror by mutableStateOf(settings.updateMirror)
    var busy by mutableStateOf(false)
    var message by mutableStateOf("")
    var found by mutableStateOf<UpdateChecker.Info?>(null)

    fun setAuto(v: Boolean) {
        autoUpdate = v
        settings.autoUpdate = v
    }

    fun setMirror(v: String) {
        mirror = v
        settings.updateMirror = v
    }
}

@Composable
private fun UpdateBanner(update: UpdateState, activity: ComponentActivity) {
    val info = update.found ?: return
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "发现新版本 ${info.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (info.notes.isNotBlank()) {
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            TextButton(
                enabled = !update.busy,
                onClick = { scope.launch { downloadAndInstall(activity, update, info) } },
            ) { Text("更新") }
        }
    }
}

@Composable
private fun UpdateSection(activity: ComponentActivity, update: UpdateState) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自动检查更新", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "启动时静默检查，有新版本会在首页提示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Switch(checked = update.autoUpdate, onCheckedChange = { update.setAuto(it) })
        }

        OutlinedTextField(
            value = update.mirror,
            onValueChange = { update.setMirror(it) },
            label = { Text("GitHub 镜像前缀（可选）") },
            placeholder = { Text("https://ghfast.top") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "留空则直连 github.com；填了就把 GitHub 地址套在这个前缀后面",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                enabled = !update.busy,
                onClick = { scope.launch { checkForUpdate(activity, update, install = true) } },
            ) { Text("检查并更新") }
            if (update.message.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    update.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private suspend fun checkForUpdate(ctx: Context, st: UpdateState, install: Boolean) {
    st.busy = true
    st.message = "正在检查…"
    val info = UpdateChecker.check(UpdateChecker.DEFAULT_MANIFEST, st.mirror)
    st.busy = false
    when {
        info == null -> {
            st.message = "检查失败：拉不到版本信息"
            st.found = null
        }
        info.versionCode <= BuildConfig.VERSION_CODE -> {
            st.message = "已是最新 v${BuildConfig.VERSION_NAME}"
            st.found = null
        }
        else -> {
            st.found = info
            st.message = "发现 v${info.versionName}"
            if (install) downloadAndInstall(ctx, st, info)
        }
    }
}

private suspend fun downloadAndInstall(ctx: Context, st: UpdateState, info: UpdateChecker.Info) {
    if (!ApkInstaller.canInstall(ctx)) {
        ApkInstaller.openInstallPermission(ctx)
        st.message = "请先允许安装未知来源应用"
        return
    }
    val dest = File(ctx.cacheDir, "update/app-debug.apk")
    st.busy = true
    st.message = "准备下载…"
    runCatching {
        UpdateChecker.download(info.apkUrl, st.mirror, dest) { p ->
            st.message = "下载中 ${(p * 100).toInt()}%"
        }
    }.onSuccess {
        st.message = if (ApkInstaller.install(ctx, dest)) "已调起安装" else "无法调起安装器"
    }.onFailure {
        st.message = "下载失败：${it.message ?: "未知错误"}"
    }
    st.busy = false
}
