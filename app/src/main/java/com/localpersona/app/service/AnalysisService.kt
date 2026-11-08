package com.localpersona.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.localpersona.app.MainActivity
import com.localpersona.app.PersonaApp
import com.localpersona.app.R
import com.localpersona.app.engine.AnalysisEngine
import com.localpersona.app.engine.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台服务：让推理在切后台、锁屏后继续跑，同时托管悬浮窗进度条。
 */
class AnalysisService : Service() {

    companion object {
        const val CHANNEL_ID = "persona_analysis"
        const val NOTIF_ID = 47
        const val ACTION_CANCEL = "com.localpersona.app.action.CANCEL"
        const val ACTION_DOWNLOAD = "com.localpersona.app.action.DOWNLOAD"

        fun start(ctx: Context) {
            val intent = Intent(ctx, AnalysisService::class.java)
            ContextCompat.startForegroundService(ctx, intent)
        }

        fun download(ctx: Context) {
            val intent = Intent(ctx, AnalysisService::class.java).setAction(ACTION_DOWNLOAD)
            ContextCompat.startForegroundService(ctx, intent)
        }

        fun cancel(ctx: Context) {
            AnalysisEngine.cancel()
            runCatching { ctx.stopService(Intent(ctx, AnalysisService::class.java)) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: OverlayProgress? = null
    private var watcher: Job? = null
    private var lastNotify = 0L

    override fun onCreate() {
        super.onCreate()
        AnalysisEngine.attach(this)
        overlay = OverlayProgress(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            AnalysisEngine.cancel()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification(AnalysisEngine.state.value))
        if (intent?.action == ACTION_DOWNLOAD) {
            AnalysisEngine.downloadOnly()
        } else {
            AnalysisEngine.start()
        }

        if (watcher == null) {
            watcher = scope.launch {
                AnalysisEngine.state.collect { st ->
                    updateOverlay(st)
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 700 || !st.busy) {
                        lastNotify = now
                        runCatching { notify(buildNotification(st)) }
                    }
                    if (st.finished) {
                        ServiceCompat.stopForeground(this@AnalysisService, ServiceCompat.STOP_FOREGROUND_DETACH)
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun updateOverlay(st: UiState) {
        val o = overlay ?: return
        if (st.busy && st.overlayEnabled && o.canDraw()) {
            o.show()
            o.update(st)
        } else {
            o.hide()
        }
    }

    private fun notify(n: Notification) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, n)
    }

    private fun buildNotification(st: UiState): Notification {
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this,
            2,
            Intent(this, AnalysisService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val pct = (st.progress.coerceIn(0f, 1f) * 100).toInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_persona)
            .setContentTitle(if (st.finished) "本地画像 · 完成" else "本地画像 · $pct%")
            .setContentText(st.status)
            .setOngoing(st.busy)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, pct, st.phase == com.localpersona.app.engine.Phase.SCANNING)
            .setContentIntent(open)
            .apply { if (st.busy) addAction(0, "取消", cancel) }
            .build()
    }

    override fun onDestroy() {
        overlay?.hide()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
