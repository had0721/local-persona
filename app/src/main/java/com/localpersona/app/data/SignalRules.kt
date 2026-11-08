package com.localpersona.app.data

/**
 * 从“装了哪些应用”里能读出的强信号。全部是本地规则，不做任何推断式猜测。
 *
 * 单条高价值信号（移民、宗教、孕期、殡葬…）比十个普通类目更有区分度，
 * 所以这里宁可多写规则，也不让模型去猜。
 */
object SignalRules {

    private class Rule(val message: String, val keys: List<String>, val min: Int = 1)

    private val rules: List<Rule> = listOf(
        Rule("装有代理/翻墙类工具", listOf("v2ray", "clash", "shadowsocks", "sing-box", "sagernet", "wireguard", "surfboard", "surfshark", "nordvpn", "expressvpn", "tunnelbear", "psiphon", "orbot", "hiddify", "nekobox", "xray", "trojan")),
        Rule("装有加密资产相关 App", listOf("binance", "okex", "okx", "coinbase", "metamask", "trustwallet", "imtoken", "bybit", "kraken", "kucoin", "bitget", "huobi", "ledger", "phantom", "uniswap")),
        Rule("装有开发者工具（终端/GitHub/IDE 一类）", listOf("termux", "github", "androidide", "acode", "dev.editor", "adb", "magisk", "superuser", "kernelmanager", "anlinux", "userland", "replit", "webide", "juicessh", "termius", "git")),
        Rule("装有多个券商/行情 App，有投资习惯", listOf("eastmoney", "hexin", "tonghuashun", "futu", "tiger", "xueqiu", "zhangle", "gtja", "htsc"), 2),
        Rule("装了多个银行 App，账户体系较复杂", listOf("com.icbc", "com.ccb", "com.abchina", "com.bankcomm", "cmb.pb", "com.cmbchina", "com.pingan", "com.spdb", "com.ecitic", "com.cib", "com.cgbchina", "chase", "wellsfargo", "bankofamerica"), 3),
        Rule("海外服务 App 占比明显（同时使用国内外生态）", listOf("org.telegram", "com.whatsapp", "com.instagram", "com.twitter", "com.discord", "com.google.android", "com.spotify", "com.netflix", "jp.naver.line", "com.facebook", "com.snapchat", "com.linkedin", "com.ubercab", "com.airbnb", "com.amazon"), 3),
        Rule("装了多个陌生人社交 App", listOf("immomo", "soul", "putong", "tinder", "bumble", "badoo"), 2),
        Rule("装有母婴/育儿类 App", listOf("babytree", "baby", "mama", "beva", "yuer", "qinbaobao", "母婴", "育儿")),
        Rule("装有宠物相关 App", listOf("boqii", "chongwu", "petlife", "宠物", "dog", "cat")),
        Rule("装有运动健身类 App", listOf("keep", "codoon", "strava", "fitbit", "fitness", "yodo"), 2),
        Rule("装有车主/看车类 App", listOf("tesla", "nio", "lixiang", "byd", "xpeng", "autohome", "dongchedi", "理想", "蔚来")),
        Rule("装有房产/租房类 App", listOf("beike", "lianjia", "anjuke", "fang", "ke.com", "链家", "贝壳")),
        Rule("装有语言/考试类学习 App", listOf("duolingo", "youdao", "eudic", "baicizhan", "zuoyebang", "xueersi", "作业帮", "考研", "托福", "雅思"), 2),
        Rule("二次元内容与游戏浓度高", listOf("bilibili", "mihoyo", "genshin", "honkai", "arknights", "yostar", "comic", "acg", "nijigasaki"), 3),
        Rule("装有演出/票务类 App", listOf("damai", "maoyan", "piaoxingqiu", "gewara", "大麦", "猫眼")),
        Rule("装有主机/PC 游戏平台 App", listOf("steam", "nintendo", "playstation", "epicgames", "xbox"), 2),
        Rule("装有多个阅读/小说 App", listOf("qidian", "weread", "ireader", "zhangyue", "fanqie", "番茄", "起点", "掌阅"), 2),
        Rule("装有医疗健康类 App", listOf("health", "hospital", "doctor", "dxy", "haodf", "pills", "医保", "挂号"), 2),

        // ---- 高价值单条信号 ----
        Rule("涉及移民/出国留学（签证、绿卡、语言考试类）", listOf("uscis", "immigration", "visa", "ielts", "gre", "toefl", "移民", "绿卡", "留学", "签证")),
        Rule("装有宗教类应用", listOf("bible", "quran", "佛经", "念经", "church", "prayer", "圣经", "古兰经", "禅修")),
        Rule("涉及孕期/备孕", listOf("pregnancy", "孕期", "胎教", "备孕", "孕产")),
        Rule("装有境外银行/支付（Revolut、Wise 一类）", listOf("revolut", "monzo", "wise.com", "hsbc", "citi", "n26", "chime", "zelle")),
        Rule("涉及殡葬/纪念类", listOf("殡葬", "纪念堂", "funeral", "cemetery")),
        Rule("装有航空常旅客/航班类 App", listOf("umetrip", "variflight", "航旅", "飞常准", "mileage", "常旅客", "lufthansa")),
        Rule("装有潮玩/手办/谷子类 App", listOf("popmart", "泡泡玛特", "手办", "谷子", "bilibili.mall", "molichou")),
        Rule("装有菜谱/烘焙类 App", listOf("xiachufang", "下厨房", "菜谱", "烘焙", "kitchen", "recipe")),
        Rule("装有戏曲/养生/大字类 App（偏年长）", listOf("养生", "广场舞", "戏曲", "大字", "健康之路", "elder")),
        Rule("装有校园/课表类 App（学生）", listOf("supercamp", "课程表", "校园", "school", "campus", "学生")),
        Rule("装有快递/物流管理类 App", listOf("cainiao", "菜鸟", "sf-express", "顺丰", "kuaidi", "快递")),
        Rule("装有海淘/代购类 App", listOf("bieber", "考拉", "sogo", "haitao", "海淘", "代购", "洋码头")),
        Rule("装有乐器/调音类 App", listOf("guitar", "piano", "tuner", "metronome", "吉他", "钢琴", "调音")),
        Rule("装有无人机/摄影器材类 App", listOf("dji", "gopro", "insta360", "dji.go.v5")),
        Rule("装有美妆/护肤类 App", listOf("sephora", "丝芙兰", "perfectdiary", "完美日记", "skincare", "cosmetic")),
        Rule("装有二手奢侈品/寄卖类 App", listOf("hongbulin", "红布林", "only2", "只二", "plum", "奢侈品")),
        Rule("装有播客类 App", listOf("xiaoyuzhou", "小宇宙", "podcast", "castbox", "overcast")),
        Rule("装有笔记/知识管理类 App", listOf("notion", "obsidian", "evernote", "roam", "logseq", "flomo")),
        Rule("装有多个 AI 助手 App", listOf("openai", "chatgpt", "claude", "deepseek", "kimi", "nova", "gemini", "copilot", "tongyi", "doubao"), 2),
        Rule("装有智能家居类 App", listOf("smarthome", "tuya", "mijia", "米家", "hilink", "smartlife")),
        Rule("装有政务/社保/交管类 App", listOf("tmri", "chinatax", "社保", "交管", "政务", "12345")),
        Rule("装有婚庆/摄影/婚礼类 App", listOf("wedding", "婚礼", "婚纱", "请柬")),
        Rule("装有境外出行/地图类 App", listOf("google.android.apps.maps", "uber", "bolt", "grab", "citymapper")),
        Rule("装有理财/基金/记账类 App", listOf("fund", "jijin", "基金", "理财", "随手记", "moneywiz", "记账")),
        Rule("装有众筹/公益类 App", listOf("shuidichou", "水滴筹", "gongyi", "公益", "kickstarter")),
        Rule("装有多个浏览器", listOf("chrome", "firefox", "browser", "edge", "opera", "mtt", "ucmobile"), 2),
        Rule("装有多个输入法", listOf("inputmethod", "wetype", "sogou", "baidu.input", "iflytek", "gokeyboard"), 2),
        Rule("装有多个地图/导航", listOf("minimap", "baidumap", "maps", "waze", "here"), 2),
        Rule("装有多个短视频 App", listOf("aweme", "gifmaker", "musically", "kuaishou"), 2),
        Rule("装有多个长视频 App", listOf("qiyi", "qqlive", "youku", "imgo", "netflix", "bilibili"), 3),
        Rule("装有多个音乐 App", listOf("cloudmusic", "qqmusic", "kugou", "kuwo", "spotify", "xiami"), 3),
        Rule("装有多个外卖/团购 App", listOf("ele", "meituan", "dianping", "doordash", "ubereats"), 2),
        Rule("装有多个招聘 App", listOf("bosszhipin", "lagou", "zhaopin", "51job", "linkedin"), 2),
        Rule("装有多个游戏（10 个以上）", listOf("tmgp", "mihoyo", "supercell", "game", ".gp", "roblox", "minecraft"), 10),
        Rule("桌面启动器/主题/美化类改装", listOf("launcher", "theme", "iconpack", "kwgt", "nova")),
        Rule("装有 root/系统改装工具", listOf("magisk", "supersu", "xposed", "lsposed", "twrp", "kernel")),
        Rule("装有测试/诊断类工具", listOf("devcheck", "cpu-z", "aida64", "network analyzer", "sysdiagnose")),
        Rule("装有虚拟货币挖矿/节点类", listOf("miner", "staking", "validator", "node")),
    )

    private fun count(pkgs: Collection<String>, keys: List<String>): Int =
        pkgs.count { p -> keys.any { p.contains(it, ignoreCase = true) } }

    fun detect(pkgs: Collection<String>): List<String> =
        rules.filter { count(pkgs, it.keys) >= it.min }.map { it.message }
}
