package com.localpersona.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localpersona.app.llm.ModelManager
import com.localpersona.app.ui.Accent
import com.localpersona.app.ui.Mono
import com.localpersona.app.ui.PersonaTheme
import com.localpersona.app.ui.PersonaViewModel
import com.localpersona.app.ui.Phase
import com.localpersona.app.ui.UiState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PersonaTheme {
                val vm: PersonaViewModel = viewModel()
                PersonaScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaScreen(vm: PersonaViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Column {
                        Text(
                            "本地画像",
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            "ON-DEVICE · 不联网 · 零运行时权限",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            Icons.Outlined.Settings,
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
            MetricRow(state)
            if (state.enrichText.isNotBlank()) EnrichLine(state.enrichText)
            StepLine(state.step)
            ActionButton(state, vm)
            if (state.busy) ProgressBlock(state)
            ErrorBlock(state.error)
            ResultBlock(state)
            if (state.showSeed && state.seedText.isNotBlank()) SeedBlock(state.seedText)
            Footer(state)
        }
    }

    if (showSettings) {
        SettingsDialog(state, vm) { showSettings = false }
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
            if (state.elapsedMs > 0) String.format("%.1f s", state.elapsedMs / 1000.0) else "—",
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
private fun ActionButton(state: UiState, vm: PersonaViewModel) {
    val label = when {
        state.busy -> "停止"
        !state.modelReady -> "下载模型（约 508 MB）"
        else -> "开始分析"
    }
    Button(
        onClick = {
            if (state.busy) vm.cancel() else vm.startAnalysis()
        },
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
private fun ProgressBlock(state: UiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.phase == Phase.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = Accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
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
private fun ResultBlock(state: UiState) {
    if (state.streamed.isBlank() && state.phase != Phase.GENERATING) return
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
                    Text(
                        "生成中",
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent,
                    )
                } else if (state.tokensPerSec > 0f) {
                    Text(
                        String.format("%.1f tok/s", state.tokensPerSec),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
            SelectionContainer {
                Text(
                    state.streamed.ifBlank { "…" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
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
            textAlign = TextAlign.Start,
        )
        Text(
            "读取的只有包名与机型，不含聊天、照片、位置或账号。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingsDialog(state: UiState, vm: PersonaViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("联网识别未知应用", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "只把包名和机型拿去查“这是什么”，2.4 秒硬上限，结果缓存 30 天",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.searchEnabled, onCheckedChange = { vm.setSearchEnabled(it) })
                }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("显示送给模型的内容", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "方便核对“它到底看到了什么”",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.showSeed, onCheckedChange = { vm.setShowSeed(it) })
                }

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
                        TextButton(onClick = { vm.downloadModelOnly(); onDismiss() }) {
                            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("下载")
                        }
                        TextButton(onClick = { vm.deleteModel() }) {
                            Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("删除")
                        }
                    }
                }

                HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outline)

                Text(
                    "本地画像 v0.1 · llama.cpp + Qwen3.5-0.8B",
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
