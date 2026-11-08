package com.localpersona.app.data

/** 从“装了哪些应用”里能读出的强信号。全部是本地规则，不做任何推断式猜测。 */
object SignalRules {

    private val proxy = listOf(
        "v2ray", "clash", "shadowsocks", "sing-box", "sagernet", "wireguard",
        "surfboard", "surfshark", "nordvpn", "expressvpn", "tunnelbear", "psiphon",
        "tor", "orbot", "hiddify", "nekobox", "nekoray", "xray", "trojan", "ssr",
    )
    private val crypto = listOf(
        "binance", "okex", "okx", "coinbase", "metamask", "trustwallet", "token.im",
        "imtoken", "bybit", "kraken", "kucoin", "bitget", "gate.io", "huobi", "ledger",
    )
    private val dev = listOf(
        "termux", "github", "androidide", "acode", "dev.editor", "adb", "magisk",
        "superuser", "kernelmanager", "anlinux", "userland", "droidscript", "replit",
        "webide", "code.termux", "juicessh", "termius",
    )
    private val brokerage = listOf(
        "eastmoney", "hexin", "tonghuashun", "futu", "tiger", "xueqiu", "sohu.sns",
        "zhangle", "gtja", "htsc", "国泰", "东方财富", "同花顺", "雪球", "富途",
    )
    private val bank = listOf(
        "com.icbc", "com.ccb", "com.abchina", "com.bankcomm", "cmb.pb", "com.cmbchina",
        "com.pingan", "com.spdb", "com.ecitic", "com.cib", "com.cgbchina", "com.chase",
        "com.wf.wellsfargomobile", "com.infonow.bofa", "com.bankofamerica",
    )
    private val overseas = listOf(
        "org.telegram", "com.whatsapp", "com.instagram", "com.twitter", "com.discord",
        "com.google.android", "com.spotify", "com.netflix", "jp.naver.line", "com.facebook",
        "com.snapchat", "com.linkedin", "com.ubercab", "com.airbnb", "com.amazon",
    )
    private val dating = listOf("immomo", "soul", "putong", "tinder", "bumble", "tan", "badoo")
    private val baby = listOf("babytree", "baby", "mama", "beva", "yuer", "qinbaobao", "母婴", "育儿")
    private val pet = listOf("pet", "chongwu", "boqii", "宠物", "dog", "cat")
    private val fitness = listOf("keep", "codoon", "strava", "fitbit", "fitness", "yodo", "gps")
    private val car = listOf("tesla", "nio", "lixiang", "byd", "xpeng", "autohome", "dongchedi", "理想", "蔚来")
    private val house = listOf("beike", "lianjia", "anjuke", "fang", "ke.com", "链家", "贝壳")
    private val education = listOf("duolingo", "youdao", "eudic", "baicizhan", "zuoyebang", "xueersi", "作业帮", "考研", "托福", "雅思")
    private val anime = listOf("bilibili", "mihoyo", "genshin", "honkai", "arknights", "yostar", "comic", "acg", "二次元")
    private val ticketing = listOf("damai", "maoyan", "piaoxingqiu", "gewara", "大麦", "猫眼")
    private val otaku = listOf("steam", "nintendo", "playstation", "epicgames", "xbox")
    private val novel = listOf("qidian", "weread", "ireader", "zhangyue", "fanqie", "番茄", "起点", "掌阅")
    private val health = listOf("health", "hospital", "doctor", "dxy", "haodf", "pills", "医保", "挂号")

    private fun count(pkgs: Collection<String>, keys: List<String>): Int =
        pkgs.count { p -> keys.any { p.contains(it, ignoreCase = true) } }

    fun detect(pkgs: Collection<String>): List<String> {
        val out = ArrayList<String>()
        if (count(pkgs, proxy) >= 1) out.add("装有代理/翻墙类工具")
        if (count(pkgs, crypto) >= 1) out.add("装有加密资产相关 App")
        if (count(pkgs, dev) >= 1) out.add("装有开发者工具（终端/GitHub/IDE 一类）")
        val banks = count(pkgs, bank)
        if (banks >= 3) out.add("装了 $banks 个银行 App，账户体系较复杂")
        if (count(pkgs, brokerage) >= 2) out.add("装了多个券商/行情 App，有投资习惯")
        if (count(pkgs, overseas) >= 3) out.add("海外服务 App 占比明显（同时使用国内外生态）")
        if (count(pkgs, dating) >= 2) out.add("装了多个陌生人社交 App")
        if (count(pkgs, baby) >= 1) out.add("装有母婴/育儿类 App")
        if (count(pkgs, pet) >= 1) out.add("装有宠物相关 App")
        if (count(pkgs, fitness) >= 2) out.add("装有运动健身类 App")
        if (count(pkgs, car) >= 1) out.add("装有车主/看车类 App")
        if (count(pkgs, house) >= 1) out.add("装有房产/租房类 App")
        if (count(pkgs, education) >= 2) out.add("装有语言/考试类学习 App")
        if (count(pkgs, anime) >= 3) out.add("二次元内容与游戏浓度高")
        if (count(pkgs, ticketing) >= 1) out.add("装有演出/票务类 App")
        if (count(pkgs, otaku) >= 2) out.add("装有主机/PC 游戏平台 App")
        if (count(pkgs, novel) >= 2) out.add("装有多个阅读/小说 App")
        if (count(pkgs, health) >= 2) out.add("装有医疗健康类 App")
        return out
    }
}
