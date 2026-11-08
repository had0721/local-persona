package com.localpersona.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.localpersona.app.engine.UiState
import kotlin.math.abs

/**
 * 系统级悬浮窗进度条：叠在别的应用上面，可以拖动、可以缩成一个小胶囊、可以关掉。
 * 关掉只是移除悬浮窗，分析照旧在后台跑。
 */
class OverlayProgress(private val ctx: Context) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var label: TextView? = null
    private var percent: TextView? = null
    private var bar: ProgressBar? = null
    private var compact = false

    fun canDraw(): Boolean = Settings.canDrawOverlays(ctx)

    fun show() {
        if (root != null) return
        if (!canDraw()) return

        val container = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.argb(242, 255, 255, 255), dp(16f), Color.argb(26, 26, 107, 255))
            elevation = dp(8f).toFloat()
            setPadding(dp(14f), dp(10f), dp(14f), dp(12f))
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        label = TextView(ctx).apply {
            text = "准备中"
            textSize = 12f
            setTextColor(Color.parseColor("#0B1526"))
            maxLines = 1
        }

        percent = TextView(ctx).apply {
            text = "0%"
            textSize = 12f
            setTextColor(Color.parseColor("#1A6BFF"))
        }

        val spacer = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }

        val minimize = TextView(ctx).apply {
            text = "—"
            textSize = 15f
            setTextColor(Color.parseColor("#5C6E88"))
            setPadding(dp(10f), 0, dp(10f), 0)
            setOnClickListener { toggleCompact() }
        }

        val close = TextView(ctx).apply {
            text = "×"
            textSize = 16f
            setTextColor(Color.parseColor("#5C6E88"))
            setPadding(dp(8f), 0, 0, 0)
            setOnClickListener { hide() }
        }

        row.addView(label)
        row.addView(spacer)
        row.addView(percent)
        row.addView(minimize)
        row.addView(close)

        bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A6BFF"))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4f))
            lp.topMargin = dp(8f)
            layoutParams = lp
        }

        container.addView(row)
        container.addView(bar)

        val lp = WindowManager.LayoutParams(
            dp(230f),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            android.graphics.PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16f)
            y = dp(90f)
        }

        attachDrag(container, lp)

        root = container
        params = lp
        try {
            wm.addView(container, lp)
        } catch (t: Throwable) {
            root = null
            params = null
        }
    }

    fun update(state: UiState) {
        if (root == null) return
        label?.text = state.status.take(18)
        val p = (state.progress.coerceIn(0f, 1f) * 100).toInt()
        percent?.text = "$p%"
        bar?.progress = (state.progress.coerceIn(0f, 1f) * 1000).toInt()
    }

    fun hide() {
        val v = root ?: return
        try {
            wm.removeView(v)
        } catch (_: Throwable) {
        }
        root = null
        params = null
    }

    private fun toggleCompact() {
        compact = !compact
        label?.visibility = if (compact) View.GONE else View.VISIBLE
        bar?.visibility = if (compact) View.GONE else View.VISIBLE
        root?.setPadding(
            dp(if (compact) 10f else 14f),
            dp(if (compact) 6f else 10f),
            dp(if (compact) 8f else 14f),
            dp(if (compact) 6f else 12f),
        )
        params?.let { p ->
            p.width = if (compact) dp(120f) else dp(230f)
            root?.let { v -> runCatching { wm.updateViewLayout(v, p) } }
        }
    }

    private fun attachDrag(view: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > dp(6f) || abs(dy) > dp(6f)) {
                        lp.x = startX + dx.toInt()
                        lp.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(view, lp) }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun rounded(color: Int, radius: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
            setStroke(1, stroke)
        }

    private fun dp(v: Float): Int = (v * ctx.resources.displayMetrics.density).toInt()
}
