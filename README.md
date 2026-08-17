# The Music Lives On 8

By JHCWColin

一个 monorepo，包含两款纯原生、完全离线的本地音乐播放器（Android 8.1+ / minSdk 27 / targetSdk 34），两者**共享同一个曲库**。

## 两个 App

| 目录 | App | 包名 | 角色 |
|---|---|---|---|
| `app/` | The Music Lives On 8 | `com.jhcwcolin.musicliveson` | 共享曲库**宿主**（含 ContentProvider） |
| `lyric-app/` | The Lyric Lives On 8 | `com.jhcwcolin.lyricliveson` | 共享曲库**客户端**（歌词加强版） |

### The Music Lives On 8（宿主）
- 常规播放器页 + 设置页倍速（0.5×–2.0×）、单/双行歌词。
- 提供 `LibraryProvider`（ContentProvider，signature 级权限 `com.jhcwcolin.sharedlib.ACCESS`）暴露曲库 / 歌词 / 播放流 / 封面。

### The Lyric Lives On 8（歌词加强版，客户端）
- Apple Music 式全屏歌词：大字号、静态显示、无省略号。
- 歌词显示方式四种：单行 / 双行 / 三行居中大字，以及「全部列表」（当前句置顶、其余向下排列并淡出、左对齐、不受行数限制）。
- 倍速仅存在于设置页。

## 共享曲库

- 旧 App 作为数据源；新 App 通过 Provider 读写（任一边导入/删除，两边同步），未检测到旧 App 时自动回退本地库。
- 新 App 播放/封面走宿主 Provider 的文件流，不需要自己的 URI 授权。
- 导入任一边时自动互相授权（尽力而为；个别系统上跨应用授权可能不持久，可在两边各导入一次同一文件夹）。
- **两个 APK 必须用同一把签名证书**（本仓库根目录 `release.jks`，别名 `musicliveson`，密码 `12345678`）。`com.jhcwcolin.sharedlib.ACCESS` 为 signature 级权限，签名不一致时共享失效。

## 构建

需要本机装有 Android SDK build-tools（含 `aapt2`、`d8`、`apksigner`、`zipalign`）与 JDK 17+。工具链路径在各 `build-*.sh` 顶部按需调整。

```bash
bash build.sh           # 同时构建两个 App
bash build-music.sh     # 只构建宿主 → dist/TheMusicLivesOn8-v1.0.apk
bash build-lyric.sh     # 只构建客户端 → dist/TheLyricLivesOn8-v1.0.apk
```

产物输出到 `dist/`（本仓库包含已构建产物）。构建流程：`aapt2`（编译/链接资源并生成 `R.java`）→ `javac` → `d8`（dex）→ `zipalign` → `apksigner`（签名，`--min-sdk-version 27`）。

## 签名

- 证书：`release.jks`（自签名 RSA 2048，已随仓库提交，两个 App 共用）
- 别名：`musicliveson`
- 密钥库 / 私钥密码：`12345678`

> 该证书与密码仅用于本项目自用分发。

## 安装

- 两个 App 需同时安装（先装哪个都行）。**已装过旧签名版本时，需先卸载再安装**（签名已统一）。
- 只有两个都在且同签名时，共享才启用；否则新 App 静默使用本地库。

## 支持的音乐格式

mp3 · m4a · aac · ogg/oga · wav · flac · opus · wma · mid/midi · amr · 3gp · mp4 · m4b · aiff/aif · ape · mpc · ac3 · mka · webm 等（以设备 MediaPlayer 支持为准）。

## 其他说明

- 完全离线、无网络权限；纯 Android 框架（无 AndroidX / 无第三方依赖），每个 APK 约 55 KB。
- 全程无文本输入（导入走系统文件选择器，操作为按钮/滑块/开关/单选）。
- 针对墨水瓶（e-ink）与 1 GB RAM 低配设备优化：高对比配色、无动画、轻量视图。
- 详见 `lyric-app/README.md`（客户端差异说明）。
