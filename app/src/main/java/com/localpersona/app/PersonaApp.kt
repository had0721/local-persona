package com.localpersona.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.localpersona.app.engine.AnalysisEngine
import com.localpersona.app.service.AnalysisService

class PersonaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AnalysisEngine.attach(this)

        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            AnalysisService.CHANNEL_ID,
            "分析进度",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "本地画像在后台生成时的进度"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }
}
