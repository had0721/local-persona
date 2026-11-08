# 三步拿到 APK

仓库已经准备好，全部代码在本地校验通过。你需要做的只有「上传」和「下载」。

---

## 方式 A：让我来推（最快）

1. GitHub 网页右上角 → **New repository**
   - 名字随便，比如 `local-persona`
   - **不要**勾选 "Add a README file"
   - 选 **Private** 或 Public 都行
2. 头像 → **Settings** → **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**
   - **Repository access**：只选刚建的那个仓库
   - **Permissions → Repository permissions**：
     - `Contents` → **Read and write**
     - `Workflows` → **Read and write**
   - **Expiration**：7 days
3. 把 token 发给我。我会建 `main` 分支、推送、触发构建。

> 用完记得点 **Revoke**。我只用它推送代码，不会写进任何文件。

---

## 方式 B：你自己推

解压源码包，在解压出来的 `localpersona/` 目录里执行：

```bash
git init -b main
git add -A
git commit -m "init"
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

（或者直接用 GitHub 网页的 "uploading an existing file" 把整个文件夹拖上去，注意要包含 `.github/workflows/android.yml`。）

---

## 然后

推送完成后，仓库页面 → **Actions** 标签：

1. 会自动出现一个 **Build APK** 的构建
2. 等它跑完（首次约 12~18 分钟，大头是编译 llama.cpp）
3. 点进这次运行 → 页面底部 **Artifacts** → 下载 `LocalPersona-apk`
4. 解压得到 `app-debug.apk` → 传到手机 → 允许「未知来源」安装

## 装好之后

- 首次打开点「下载模型（约 508 MB）」，从 hf-mirror 拉取，支持断点续传
- 下完点「开始分析」，约 10~20 秒出结果
- 设置里可以打开「显示送给模型的内容」，看看它到底看到了什么

## 如果构建失败

把 Actions 里失败那一步的日志贴给我，重点是这几步：
- `Build libpersona.so for arm64-v8a`
- `Assemble debug APK`

已知可能需要调的地方：NDK 版本、`GGML_CPU_KLEIDIAI`（失败就改 `OFF`，只是推理慢一点）。
