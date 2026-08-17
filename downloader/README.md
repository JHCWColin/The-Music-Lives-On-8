# The Downloader Lives On 8

By abpdf/JHCWColin

Tauri 2 + SvelteKit 音乐下载器，是 [musicdownloader](https://github.com/abpdf/musicdownloader) 的墨水瓶优化分支。
唯一的核心改动：**下载的音频直接存入音乐库文件夹**（系统 Music 下的 `The Music Lives On 8`），而不是原来的 `musicdownloaded`。

## 功能

- 通过 祈杰のMeting-API 搜索 / 下载音乐（mp3、aac 等），点击结果自动开始下载。
- 打开 gequhai.com 作为备用下载站。
- 顶部标题栏：`The Downloader Lives On 8` + 小字 `By abpdf/JHCWColin`。
- 墨水瓶（e-ink）优化：纯黑白高对比配色，全部关闭动画 / 过渡 / 阴影，避免残影。

## 与音乐库的关系

- 下载的文件落在 `Music/The Music Lives On 8/`。在两个音乐 App（`app/` 与 `lyric-app/`）里把这个文件夹导入曲库（SAF 授权一次），之后每次下载都会自动出现在曲库中。
- Android 目标（`src-tauri/gen/android/`）已声明共享曲库的 signature 级权限 `com.jhcwcolin.sharedlib.ACCESS`；如需在 Android 上直接写入共享曲库，需用本仓库根目录 `release.jks` 签名（与两个音乐 App 相同），再做内容 Provider 对接。

## 开发

```bash
npm install
npm run dev        # 前端 dev（http://localhost:1420）
npm run build      # 前端构建 -> build/
```

## 打包（需要 Rust 工具链，本机未安装）

```bash
npx tauri build            # 桌面安装包（Windows/macOS/Linux）
npx tauri android build    # Android APK（需 NDK；且 dl.google.com 在本机不可达，Gradle/AGP 拉不到）
```

本仓库只包含并提交了**前端构建产物**（`build/`）。Tauri 的桌面 / Android 二进制因本机没有 Rust/cargo 而无法在此环境生成。

## 许可证

MIT OR Apache-2.0（Copyright © 2026 abpdf / JHCWColin）。
