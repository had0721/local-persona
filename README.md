# 本地画像 · Local Persona

一个 Android 小应用：**只用「装了哪些应用 + 什么机型」，在本机跑 Qwen3.5-0.8B，画出一个人的画像。**

初衷是做个反证：不给通讯录、不给位置、不给相册、不读聊天记录，仅凭公开可得的装机信息，也能推断出相当多东西。

---

## 它到底读什么

| 数据 | 来源 | 权限 |
| --- | --- | --- |
| 已安装应用包名 | `PackageManager.getInstalledPackages` | `QUERY_ALL_PACKAGES`（普通权限，无弹窗） |
| 机型、品牌、系统版本、ABI | `Build.*` | 无 |
| 屏幕、内存、存储、语言、时区、字体缩放 | `DisplayMetrics` / `ActivityManager` / `Configuration` | 无 |

不读：账号、联系人、短信、相册、位置、剪贴板、使用时长、网络流量。

> 「零权限」的说法不严谨——读应用列表必须声明 `QUERY_ALL_PACKAGES`。它不会弹窗，但 Google Play 上架需要申报用途。自签名安装无影响。

---

## 架构：为什么它不慢

0.8B 模型直接读 200 个包名，既慢又不准。所以脏活全部交给本地规则，模型只负责写文案。

```
采集 (≈50ms)      包名 + Build，不取应用名（loadLabel 是隐藏的 1~3 秒）
   ↓
映射 (≈10ms)      assets/app_map.tsv：包名 → 类目，前缀规则兜底长尾
   ↓
压缩             200 个包 → 12 行类目统计 + 若干强信号，prompt ≈ 300 token
   ↓
联网补全(可选)    只查「本地没认出来的前 6 个包」和「这个机型是什么」
   ↓
推理 (8~15s)      单次生成，关闭 thinking，流式输出
```

### 联网那一步是怎么压到几乎不花时间的

- **只查缺口**：本地映射表认得的包一个都不查；最多 6 个未知包 + 1 次机型。
- **并发**：这几条查询同时发出，整体硬超时 2.4 秒，超时就用已有结果继续。
- **和模型加载重叠**：模型读进内存要 2~5 秒，网络请求就藏在这段时间里，实际不额外等待。
- **缓存 30 天**：结果按包名/机型落盘，第二次分析 0 毫秒。
- **不做二次推理**：抓到网页只做关键词归类（本地词表打分），不把搜索结果再喂给模型。

搜索引擎用的是 Bing / Baidu 的 HTML 页（并发，谁先回用谁），不依赖 DuckDuckGo——它在国内基本连不上。取不到就静默降级成纯本地。

### 其它提速点

- 模型**常驻内存**：进 App 就开始加载，第二次分析省掉 2~5 秒。
- **关闭 thinking**：Qwen3.5 非思考模式在 assistant 前缀里塞一个空的 `<think></think>`，省掉 2~3 倍 token。
- **上下文固定 2048**，输出上限 384 token，`n_batch = 512` 分块预填。
- 流式回调按 66ms 节流，避免把 Compose 压垮。

---

## 构建（GitHub Actions 云编译）

项目不带 Gradle wrapper，CI 用 `gradle/actions/setup-gradle` 安装 Gradle 8.10.2。

1. 把仓库推到 GitHub，默认分支选 `main`。
2. Actions 会自动跑 `.github/workflows/android.yml`：
   - `ubuntu-24.04` 自带 Android SDK 与 NDK；
   - clone `llama.cpp` 到 `v0.9.0`（该版本已支持 Qwen3.5 架构）；
   - CMake + NDK 交叉编译 `libpersona.so`（arm64-v8a）；
   - `gradle :app:assembleDebug`；
   - 产物在 Artifacts → `LocalPersona-apk`。
3. 下载 APK，允许「未知来源」安装。

本机构建（需要 SDK + NDK + CMake 3.22+）：

```bash
git clone --depth 1 --branch v0.9.0 https://github.com/ggml-org/llama.cpp
cmake -S app/src/main/cpp -B build-native \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26
cmake --build build-native -j$(nproc)
cp build-native/libpersona.so app/src/main/jniLibs/arm64-v8a/
gradle :app:assembleDebug
```

## 模型

不塞进 APK（Q4_K_M 有 508 MB）。首次启动从 `hf-mirror.com` 下载
`unsloth/Qwen3.5-0.8B-GGUF` 的 `Qwen3.5-0.8B-Q4_K_M.gguf`，支持断点续传。

最低建议 6 GB 内存；推理峰值约 900 MB。

## 目录

```
app/src/main/
├── assets/app_map.tsv            包名 → 类目（本地映射表）
├── cpp/persona_jni.cpp           llama.cpp 的 JNI 桥
├── java/com/localpersona/app/
│   ├── data/                     设备事实、扫描、映射、信号、特征压缩
│   ├── llm/                      模型下载、提示词、JNI 封装
│   ├── net/                      受限联网识别 + 30 天缓存
│   └── ui/                       Compose 界面与状态机
└── res/                          蓝白主题与图标
```

## 已知限制

- 0.8B 的画像会是「套路话」，靠固定四段模板 + 信号列表撑住质量；想更准要换 2B。
- 只支持 arm64-v8a，32 位设备装不上。
- `QUERY_ALL_PACKAGES` 在国内商店一般放行，Google Play 需申报。
- 机型检索偶尔会抓到无关页面，缓存里可能留一条废话；清缓存即可。
