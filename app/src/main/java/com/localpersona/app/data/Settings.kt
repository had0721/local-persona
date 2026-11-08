package com.localpersona.app.data

import android.content.Context

class Settings(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("persona", Context.MODE_PRIVATE)

    /** 联网只用来识别“这个包/这台机是什么”，默认开启，失败不影响本地流程。 */
    var searchEnabled: Boolean
        get() = sp.getBoolean("search_enabled", true)
        set(v) = sp.edit().putBoolean("search_enabled", v).apply()

    var threads: Int
        get() = sp.getInt("threads", 0)
        set(v) = sp.edit().putInt("threads", v).apply()

    var maxTokens: Int
        get() = sp.getInt("max_tokens", 384)
        set(v) = sp.edit().putInt("max_tokens", v).apply()

    var showSeed: Boolean
        get() = sp.getBoolean("show_seed", false)
        set(v) = sp.edit().putBoolean("show_seed", v).apply()
}
