# The Lyric Lives On 8

By JHCWColin

面向 Android 8.1（API 27）及以上系统的纯原生、完全离线的本地音乐播放器。
本作是《The Music Lives On 8》的歌词加强版，可与其同时安装在同一台设备上（包名不同）。

## 特性

- 完全离线，无任何联网功能
- 纯 Android 框架实现（无 AndroidX、无第三方依赖），APK 仅约 50 KB
- Apple Music 式全屏歌词界面：歌词区域极大、大字号、静态显示（不滚动）、不用省略号截断
- 歌词显示方式四种可选：单行 / 双行 / 三行居中大字，以及「全部列表」模式（当前句置顶、其余向下排列并逐渐淡出、左对齐、不受行数限制）
- 可与《The Music Lives On 8》**共享同一个曲库**（旧 App 提供 ContentProvider，本 App 读写；需同一签名，见下）
- 倍速 0.5×–2.0× 仅在设置页调节，播放器页不显示倍速
- 支持导入单个音乐文件、单个 `.lrc` 歌词文件或整个文件夹（系统文件选择器，全程无文本输入）
- 自动永久入库（持久化 URI 授权 + SQLite）
- LRC 歌词自动匹配（按同名文件，兼容 UTF-8 / GB18030 编码）
- 支持封面显示（内嵌封面 + 文件夹内 `cover` / `folder` / `album` / `front` 封面图）
- 三个页面：音乐库（可导入）、播放器（看歌词）、设置
- 针对墨水瓶（e-ink）与 1 GB RAM 低配设备优化：高对比配色、无动画、轻量视图
- 顶部标题 “The Lyric Lives On 8”，小字 “By JHCWColin”
- 全程禁止输入法：所有操作均为按钮 / 滑块 / 开关 / 单选等控件

## 与《The Music Lives On 8》的差异

| 项目 | The Music Lives On 8 | The Lyric Lives On 8 |
|---|---|---|
| 包名 | `com.jhcwcolin.musicliveson` | `com.jhcwcolin.lyricliveson` |
| 歌词 | 单 / 双行 | 单 / 双 / 三行 + 全部列表（淡出），字号更大 |
| 歌词区域 | 常规 | Apple Music 全屏式大区域，无省略号 |
| 倍速位置 | 播放器页 + 设置 | 仅设置页 |

## 共享曲库（两个 App 装在同一台设备时）

- 旧 App（The Music Lives On 8）作为**数据源**：其 `LibraryProvider`（ContentProvider）暴露曲库 / 歌词 / 播放流 / 封面。
- 新 App 通过该 Provider 读写：任一边导入 / 删除，两边实时同步；新 App 未检测到旧 App 时自动回退到自己的本地库。
- 导入任一边时，会自动把文件授权互相授予对方（尽力而为；若某些系统上跨应用授权未持久化，可在两边各导入一次同一文件夹以保证都能播放）。
- **两个 APK 必须用同一把签名证书打包**（`com.jhcwcolin.sharedlib.ACCESS` 为 signature 级权限）。本目录的 `release.jks` 与旧 App 相同（别名 `musicliveson`）。

## 构建

需要本机装有 Android SDK build-tools（含 `aapt2`、`d8`、`apksigner`、`zipalign`）与 JDK 17+。路径在 `build.sh` 顶部按需调整。

```bash
bash build.sh
```

产物：`dist/TheLyricLivesOn8-v1.0.apk`

构建流程：`aapt2`（编译/链接资源并生成 `R.java`）→ `javac`（编译 Java）→ `d8`（dex）→ `zipalign` → `apksigner`（签名）。

## 签名

- 证书：`release.jks`（自签名，RSA 2048，本目录独立生成）
- 别名：`lyricliveson`
- 密钥库 / 私钥密码：`12345678`

> 注意：该证书与密码仅用于本项目自用分发，正式发布请替换为妥善保管的证书。

## 安装

将 `dist/TheLyricLivesOn8-v1.0.apk` 传到 Android 8.1+ 设备，允许「安装未知来源应用」后安装即可。与《The Music Lives On 8》包名不同，可同时共存。

## 支持的音乐格式

mp3 · m4a · aac · ogg/oga · wav · flac · opus · wma · mid/midi · amr · 3gp · mp4 · m4b · aiff/aif · ape · mpc · ac3 · mka · webm 等（以设备 MediaPlayer 支持为准）。
