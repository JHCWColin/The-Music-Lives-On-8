# The Music Lives On 8

By JHCWColin

面向 Android 8.1（API 27）及以上系统的纯原生、完全离线的本地音乐播放器。

## 特性

- 完全离线，无任何联网功能
- 纯 Android 框架实现（无 AndroidX、无第三方依赖），APK 仅约 50 KB
- 支持导入单个音乐文件、单个 `.lrc` 歌词文件或整个文件夹（走系统文件选择器，全程无文本输入）
- 自动永久入库（持久化 URI 授权 + SQLite）
- LRC 歌词与音乐自动匹配（按同名文件匹配，兼容 UTF-8 / GB18030 编码）
- 支持封面显示（读取内嵌专辑封面，并支持文件夹内 `cover` / `folder` / `album` / `front` 等封面图）
- 歌词单行 / 双行可切换（静态显示，不滚动）
- 0.5×–2.0× 倍速播放
- 三个页面：音乐库（可导入）、播放器（看歌词）、设置
- 针对墨水瓶（e-ink）与 1 GB RAM 低配设备优化：高对比配色、无动画、轻量视图
- 顶部标题显示 “The Music Lives On 8”，小字 “By JHCWColin”
- 全程禁止输入法：所有用户操作均为按钮 / 滑块 / 开关 / 单选等控件

## 构建

需要本机装有 Android SDK build-tools（含 `aapt2`、`d8`、`apksigner`、`zipalign`）与 JDK 17+。路径在 `build.sh` 顶部按需调整。

```bash
bash build.sh
```

产物：`dist/TheMusicLivesOn8-v1.0.apk`

构建流程：`aapt2`（编译/链接资源并生成 `R.java`）→ `javac`（编译 Java）→ `d8`（dex）→ `zipalign` → `apksigner`（签名）。

## 签名

- 证书：`release.jks`（自签名，RSA 2048）
- 别名：`musicliveson`
- 密钥库 / 私钥密码：`12345678`

> 注意：该证书与密码仅用于本项目自用分发，正式发布请替换为妥善保管的证书。

## 安装

将 `dist/TheMusicLivesOn8-v1.0.apk` 传到 Android 8.1+ 设备，允许「安装未知来源应用」后安装即可。

## 支持的音乐格式

mp3 · m4a · aac · ogg/oga · wav · flac · opus · wma · mid/midi · amr · 3gp · mp4 · m4b · aiff/aif · ape · mpc · ac3 · mka · webm 等（以设备 MediaPlayer 支持为准）。
