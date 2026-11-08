package com.localpersona.app.data

/**
 * 参照系：一个普通中国安卓用户的大致装机分布（按类目占第三方应用的比例）。
 *
 * 绝对数量说明不了什么——装 5 个券商的人，和一个装了 150 个应用里有 5 个券商的人
 * 是两种人。换算成「是全国平均的几倍」之后，模型的推理才有支点。
 *
 * 数值是经验估计，不是实测统计，只用于拉出相对关系。
 */
object Baseline {

    /** 类目 → 普通用户装机占比（%） */
    val pct: Map<String, Double> = mapOf(
        "社交" to 11.0,
        "电商" to 8.0,
        "游戏" to 10.0,
        "短视频" to 4.0,
        "长视频" to 5.0,
        "音乐" to 4.5,
        "资讯" to 3.0,
        "阅读" to 3.0,
        "工具" to 9.0,
        "办公" to 3.0,
        "效率" to 2.5,
        "摄影" to 4.0,
        "生活服务" to 2.0,
        "外卖" to 2.0,
        "出行" to 3.0,
        "地图" to 1.0,
        "旅行" to 1.5,
        "支付" to 1.5,
        "银行" to 2.0,
        "证券" to 0.5,
        "加密金融" to 0.2,
        "健康" to 2.0,
        "学习" to 2.0,
        "AI" to 1.0,
        "二手" to 1.0,
        "安全" to 1.0,
        "浏览器" to 1.0,
        "输入法" to 0.6,
        "邮件" to 1.0,
        "智能家居" to 1.0,
        "汽车" to 0.5,
        "招聘" to 0.4,
        "房产" to 0.4,
        "母婴" to 0.4,
        "宠物" to 0.3,
        "婚恋" to 0.4,
        "票务" to 0.4,
        "网络工具" to 0.3,
        "开发" to 0.4,
        "海外服务" to 2.0,
        "政务" to 0.4,
        "腾讯系" to 1.0,
    )

    private const val DEFAULT_PCT = 0.3

    fun baselineOf(category: String): Double = pct[category] ?: DEFAULT_PCT

    /** 谁都有、几乎没有区分度的应用，从分布里剔除。 */
    val universalPackages: Set<String> = setOf(
        "com.tencent.mm",
        "com.eg.android.AlipayGphone",
        "com.autonavi.minimap",
        "com.baidu.BaiduMap",
        "com.sohu.inputmethod.sogou",
        "com.baidu.input",
        "com.iflytek.inputmethod",
        "com.google.android.inputmethod.latin",
        "com.android.chrome",
        "com.google.android.gms",
        "com.tencent.mobileqq",
        "com.tencent.wetype",
    )

    /** 整个类目都属于「通用必备」的，也剔除。 */
    val universalCategories: Set<String> = setOf(
        "系统组件",
        "Google 服务",
    )
}
