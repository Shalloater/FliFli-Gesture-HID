# FliFli Gesture HID

“翻翻 · 蓝牙手势键盘”是一个 Android 手机蓝牙 HID 自定义输入设备示例。手机会伪装成一个蓝牙 HID 设备，并支持通过手势发送可配置的输入动作。

项目宣传图（SVG 源文件、1080×1440 PNG 和五页总览）位于 [`promo/`](promo/README.md)，可直接下载[五页宣传图套装](promo/FliFli-promo-5pages-v2.zip)。

当前支持的输出类型：

- 键盘按键，如 `A`、`B`、`Page Up`、`Page Down`
- 组合键，如 `Ctrl+C`、`Ctrl+V`
- 媒体键，如播放暂停、上一曲、下一曲、音量键

## 1.5.4 更新

- 首页、连接、设置采用统一标题和底部导航；首页直接展示设备连接状态与键盘列表
- 全屏手势键盘、电脑键盘、首页、连接、设置和刷新按钮使用统一的矢量图标；连接图标随连接状态切换
- 新增五种清透柔和的皮肤，桌面图标沿用原版波浪图案并随皮肤变色
- 新增英文、简体中文、繁体中文、韩语、日语切换，并保存语言与皮肤偏好

## 当前能力

- 底部三页导航：首页、连接、设置；首页展示连接状态和可运行的键盘
- 设置支持蓝、粉、紫、绿、黑五种纯色皮肤；桌面图标保留原版圆形波浪图案，仅改变色调（桌面启动器可能延迟刷新）
- 支持英文、简体中文、繁体中文、韩语、日语；皮肤与语言选择会保存，已有键盘名称不随语言切换改写
- 可在设置页创建多个自定义键盘实例
- 当前支持的键盘种类：全屏手势键盘、电脑键盘
- 首次安装会预置一个名为“阅读器翻页”的手势键盘示例，用户可以继续编辑或删除
- 每个自定义键盘可独立配置名称、震动开关、震动级别和手势动作
- 手势动作按需添加，不会预置全部候选动作
- 点按动作支持单指、双指、三指的单击、双击、三击，并可选择全屏、左半屏、右半屏、上半屏、下半屏
- 同一个点按动作不能配置重叠区域，例如左半屏单击和上半屏单击不能同时存在
- 滑动动作默认全屏触发，支持单指、双指、三指上下左右滑，以及单指顺时针/逆时针画圈
- 通过标准 HID 报告发送键盘和 Consumer Control 信号

## 重要限制

目标设备必须支持蓝牙 HID 主机，例如：

- Windows 电脑
- macOS 电脑
- Android TV / 电视盒子
- 部分平板、部分电视

普通蓝牙音箱一般不是 HID 主机，不能作为这类输入设备的目标端。

## 系统要求

- Android 9.0 或以上
- 手机系统支持 `BluetoothHidDevice`
- Android 12 或以上需要授予“附近设备/蓝牙”权限

## 构建 APK

### Windows

在项目根目录运行：

```bat
build_apk.bat
```

### macOS / Linux

在项目根目录运行：

```bash
./build_apk.sh
```

构建成功后，APK 在：

```text
app/build/outputs/apk/debug/FliFli-v1.5.4.apk
```

应用显示名称为“翻翻”。公开项目名为“FliFli Gesture HID”。每次发布功能更新时，同时递增 `versionCode` 和 `versionName`，并按新版本号生成 `FliFli-v版本号.apk` 安装包文件名。

## 使用步骤

1. 安装并打开 App。
2. 授予蓝牙/附近设备权限。
3. 打开蓝牙。
4. 在目标设备的蓝牙设置中与手机完成配对。
5. 回到 App，在连接页选择目标设备建立 HID 连接。
6. 进入设置页，新增或编辑自定义键盘。
7. 进入底部“首页”，选择已配置的键盘运行；也可以直接添加键盘。
8. 使用点按、滑动或画圈手势发送该键盘配置的 HID 输入。

## 界面验证

构建及检查：`gradlew.bat testDebugUnitTest assembleDebug lintDebug`（macOS / Linux 使用 `./gradlew`）。需要 JDK 17 和 Android SDK 35；可通过 `GRADLE_USER_HOME` 指定 Gradle 缓存目录。

安装 debug APK 到测试模拟器后运行 `python tools/ui_smoke_test.py emulator-5556`，检查五种语言、五种皮肤及启动入口、重启后的偏好和小屏大字体显示。两个 UI 脚本目前使用 `D:/Android/Sdk/platform-tools/adb.exe`，其他环境需先将脚本内的 ADB 路径改为本机路径，并指定实际模拟器序列号。该脚本会改变测试设备内本应用的皮肤、语言与显示设置，请仅用于测试模拟器。截图、UI 树和结果写入 `app/build/ui-review/`。单页检查可使用 `python tools/ui_review.py capture screenshot-name --serial emulator-5556`。

模拟器用于界面验证；HID 连接和按键发送需要具备 Bluetooth HID Device 支持的真实 Android 手机与主机联测。

皮肤采用天空蓝、樱花粉、浅紫、薄荷绿和柔和石墨灰；浅色按钮配深色文字。图标原图为 `app/src/main/res/drawable-nodpi/ic_launcher_fanfan.png`，保持原文件不变。运行 `java tools/RecolorLauncherIcons.java` 可生成五种配色：以原图调色、提亮并降低浓烈感，黑色按亮度转为灰阶，均保留原始像素位置、波浪图案、透明度和中性色背景。

## 关于后续扩展

当前版本已经把动作层和 HID 发送层拆开，后续可以继续扩展：

- 更多键盘按键
- 更多组合键
- 更多媒体键
- 长按、宏、动作序列

## 给 Codex 的构建提示词

见 `CODEX_BUILD_PROMPT.md`。
