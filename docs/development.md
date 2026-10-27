# 开发与验证

面向使用者的下载、连接及配置指南见[项目首页](../README.md)，各版本变更见[更新记录](CHANGELOG.md)。

## 工程与构建

- `app`：主线经典蓝牙 HID 应用，最低 Android 9（API 28），使用 `BluetoothHidDevice`。
- `compat`：独立的 Android 5 BLE 实验模块，与主线共享界面和配置逻辑，使用独立应用标识；支持范围、构建及诊断见 [BLE 模块文档](../compat/README.md)。
- 主线传输实现在 `app/src/classic/java/`，共享代码和资源在 `app/src/main/`。
- 使用项目 Gradle Wrapper；当前编译 SDK 为 35，Java 编译级别为 17。Android SDK 路径通过本地 `local.properties` 配置。

在仓库根目录构建主线：

```powershell
.\gradlew.bat :app:assembleDebug
```

也可使用 `build_apk.bat`。带版本号的安装包输出到 `app/build/outputs/apk/debug/FliFli-v<version>.apk`。版本由 `app/build.gradle.kts` 中的 `versionName` 和 `versionCode` 定义；裸 `assembleDebug` 会同时构建两个模块。

## 检查命令

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :compat:testDebugUnitTest :compat:lintDebug :compat:assembleDebug
.\gradlew.bat :app:assembleDebugAndroidTest
python tools/ui_smoke_test.py emulator-5556
python tools/signal_ui_smoke_test.py emulator-5556
```

`emulator-5556` 是测试设备示例，运行前替换为实际测试模拟器的序列号。UI 脚本会改变测试应用偏好与模拟器屏幕参数，应在隔离测试环境中运行。

信号脚本使用简体中文和预置的「阅读器翻页」键盘，覆盖单选替换、组合键、混合序列保存/回读、取消、步骤编辑/删除，以及小屏大字体和平板布局，并恢复原键盘配置。截图与界面树位于 `app/build/ui-review/`。

原生 Android 回归测试位于 `app/src/androidTest/`，覆盖动作编辑、长按演示、点按识别、触控会话及设置滚动等流程。BLE 模块另有独立的传输测试。模拟器测试和发送 API 成功均不能代替两台真实设备之间的蓝牙接收验证。

## 配置与信号

- `GestureBindingEditorDialog` 使用独立草稿编辑触发手势与发送信号，外层确认后替换原条目；取消或返回不写回，保存前再检查重复手势和重叠区域。
- `SignalPickerDialog` 提供单个、组合、序列三种模式；`SignalProgram` 表示发送程序，`SignalReports` 生成对应报告。
- 组合信号最多包含 6 个普通键、修饰键及 1 个媒体或 Android Consumer Control 键；触控手势不参与同时按下的组合，可独立作为序列步骤。
- 信号序列最多 16 步，每步释放后继续下一步；间隔可选 0、100、250、500、1000、2000 毫秒，默认 100 毫秒。连续触发排队，断开连接取消剩余步骤。
- Windows 徽标键显示为 `Win` / `R Win`，保留原配置 ID `kb_meta` / `kb_right_meta`。

## 手势识别与触控传输

本机多指手势按各手指自身轨迹识别，避免落指、抬指先后造成虚假位移。点按区域取整组手指的中心；连续点击只有在手指数、位置和已配置的连击区域匹配时才合并。仅配置单击时立即触发；配置连击时按系统连击时间等待，并给予下一次正常点按的完成时间。长按、取消和拖出后返回不算点按。

远端预设触控与实时透传使用单指 HID 触摸屏报告。描述符保留 Android 槽位协议识别所需的额外空记录，实际只发送一个触点。抬起后补发计数为 0 的结束帧。额外手指会释放当前远端触点并暂停本次输入，全部抬起后恢复；多触点透传已移除。

Android 10 的槽位解析要求最大槽位索引大于 0。1.5.6 修正了原单槽位描述符导致的解析问题，因此从更早版本升级需在两端删除配对、重新读取描述符。相关协商日志使用 `FliFliHID` 标签；具体机型仍须实测。

触控队列保留按下、抬起顺序，密集移动合并为最新位置，发送上限约 60 Hz。取消、失焦、尺寸变化、后台或退出会释放触点；连接中断清空输入，发送失败释放触点并断开。退出重进使用会话隔离与释放流程，新会话在释放报告后等待 35 毫秒再发送首个触点。

安全区在系统安全范围内取最大的 3:4 矩形。框外起滑使用轨迹与边界的首次交点作为远端按下位置，支持历史采样；框外点击或始终未入框不发送，入框后移出则限制在边缘。

边缘预设按受控屏幕比例定位：顶部左/右侧与底部左/中/右采用三等分区域中点，左右边缘采用垂直中点，斜滑从右上角开始。侧边上下滑动长度约四分之一屏高，固定滑动段约 208 毫秒。具体阅读器行为由接收端配置决定，调研依据见[墨水屏手势记录](eink-gesture-research-2026-10-27.md)。

## 全屏与系统边界

全屏键盘统一使用双指向内捏合退出。触控透传申请系统允许的返回排除区域，首次返回提示，2 秒内再次返回退出；继续触控会取消返回确认。通知栏、Home 及其他系统保留手势无法由普通应用完全屏蔽。

屏幕固定默认关闭，仅调用系统确认；部分厂商会显示多层系统提示。退出时只解除本应用发起的固定，外部策略固定不擅自解除。解除固定后的锁屏由系统安全设置决定，应用不主动锁屏，也不修改该策略。

## 动画与缩略图

77 个 SVG 演示包含 18 个触控信号、45 个点按区域组合和 14 个滑动/画圈动作。已确认的独立预览及生成输入位于 [`output/gesture-previews/`](../output/gesture-previews/README.md)，同步命令为：

```powershell
python tools/sync_gesture_demos.py
```

同步到 `app/src/main/assets/gesture_demos/` 的资源随安装包离线提供；缩略图使用相同坐标原生绘制。长按时创建本地 SVG 演示窗口，关闭后释放播放资源。独立预览站点不随 App 打包。
