# FliFli BLE Android 5 实验版

独立应用 `com.example.joyvolumeremote.blecompat`，最低 Android 5.0（API 21）。可与主线 FliFli 同时安装，键盘配置、皮肤和语言分别保存。此版本只编译 BLE GATT 外设实现，不包含 `BluetoothHidDevice`，不会回退到主线的经典蓝牙 HID。

信号选择界面与主线共享，支持单个信号、同时按下的组合信号，以及最多 16 步的信号序列。每一步可使用单键或组合键，步骤间隔可调，旧配置保持兼容。触控手势选项在本版显示为不可用；BLE 报告描述符保持键盘和 Consumer Control 两种输入。发送前会检查整个序列所需的订阅，容量不足时拒绝整个新序列，保留已排队输入，避免只执行一部分。

“最低 Android 5”表示 APK 的安装门槛与代码 API 兼容目标。尚未完成 Android 5 实机及阅读器配对、翻页测试，不应作为已验证的通用兼容版发布。手机必须支持 BLE 外设广播，阅读器必须支持 BLE HID 键盘及对应翻页按键；仅有蓝牙功能不足以保证可用。

## 构建

在项目根目录运行：

```powershell
./gradlew.bat :compat:assembleDebug
```

也可运行 `build_compat_apk.bat`。实验 APK 位于：

`compat/build/outputs/apk/debug/FliFli-BLE-Compat-v1.5.4-ble-experimental.2.apk`

主线仍由 `build_apk.bat` 或 `./gradlew.bat :app:assembleDebug` 构建；应用标识、最低 Android 9、版本号和 APK 命名保持原样。裸 `assembleDebug` 会构建两个模块。

## 用现有手机进行初步验证

1. 安装名称为 **FliFli BLE · 实验版** 的 APK，无需卸载主线。测试时先断开主线与阅读器的连接，并通过主线通知的“停止”结束其蓝牙服务，避免主线仍在提供键盘输入。
2. 打开实验版并授予附近设备权限（Android 12+ 需要连接和广播两项）。Android 5–11 使用清单中的传统蓝牙权限；本版本不扫描、不需要定位权限。
3. 进入“连接”。正常状态会显示“正在广播”和当前手机蓝牙名称。名称来自系统，不会修改手机的全局蓝牙名称；名称过长时会重试不带名称的广播。
4. 在**阅读器端**搜索、配对并连接手机，确认两端的系统配对提示。点击手机上的已配对设备会提交一次 GATT 连接尝试，15 秒内没有建立链路会显示具体状态；能否启用键盘仍取决于阅读器发现 HID 服务并订阅输入。不要仅凭主线已有的经典蓝牙配对记录或阅读器的“已连接”字样判断 BLE 键盘已就绪。
5. 手机必须显示 **BLE 键盘已就绪**。只有 GATT 链路连接、尚未配对或尚未订阅键盘通知时，不会显示可运行键盘的已连接状态。
6. 从首页进入预置“阅读器翻页”手势键盘测试。若阅读器未响应，编辑键盘动作，分别测试 `Page Up`、`Page Down` 和左右方向键。键盘和媒体键使用独立报告；阅读器未订阅媒体键或处于 Boot 键盘模式时会显示具体原因。
7. 回到连接页查看“BLE 已发送”及累计动作数。此计数表示按下与松开报告均收到系统发送回调，不能代替阅读器实际翻页结果。测试断线后从阅读器重新连接、连续翻页及手机锁屏后恢复。

如果广播或配对失败，回到连接页点击刷新；已连上的设备可通过“断开”或实验版通知的“断开 / 重新广播”重置。蓝牙关闭后会清理服务，重新打开时重新注册广播。

## 诊断

如果 Neo3 等阅读器显示“已连接”，App 却未就绪：先停止主线通知中的服务，再重新连接实验版。新的诊断区分“系统已配对、尚未建立本 App 的 BLE 键盘连接”“已建立 BLE 连接、等待配对”“已建立 BLE 连接、尚未启用键盘输入”和“BLE 键盘已就绪”。旧系统记录可能对应经典蓝牙；必要时由用户在阅读器端取消该手机的旧配对，再在实验版广播期间重新搜索配对。这样会删除该手机的旧配对记录，后续主线测试也需要重新配对。

日志标签 `FliFliBLE` 记录 GATT 连接请求与回调状态、拒绝其他设备、系统配对与 ACL 事件、服务读取、键盘通知订阅结果。ACL 或配对事件不会被当作键盘就绪。

回调回归测试使用系统自带测试框架，不增加依赖：`./gradlew.bat :compat:connectedDebugAndroidTest`。测试模拟 GATT 回调，覆盖重复连接不清除订阅、过渡状态不误断线以及连接超时不误报就绪；不替代实机 BLE 测试。

如果离线环境未缓存 Gradle 的 UTP 测试插件，可构建 `:compat:assembleDebugAndroidTest`，向测试模拟器安装应用和 `compat/build/outputs/apk/androidTest/debug/compat-debug-androidTest.apk`，再执行 `adb -s emulator-5556 shell am instrument -w -r com.example.joyvolumeremote.blecompat.test/android.test.InstrumentationTestRunner`。额外的界面测试显示模拟超时诊断并保存真实屏幕截图，用于检查小屏换行；不代表真实阅读器已连接。

连接页保留原始诊断状态：不支持外设广播、GATT 注册失败、广播失败及错误码、等待配对/订阅、通知发送失败或超时。新序列超出队列容量时提示等待；实时键盘状态队列拥塞、发送中切换协议与通知失败时会断开并清除待发送数据，避免按键卡住或旧输入被发送到下一次连接。

用 ADB 收集日志（多设备时添加 `-s 序列号`）：

```powershell
adb logcat -d -s FliFliBLE:I AndroidRuntime:E
```

反馈时记录手机系统版本、连接页状态、阅读器实际结果，以及日志。新手机测试成功之后，仍需要 Android 5 用户验证硬件广播、系统配对和真实按键接收。

## 自动检查

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :compat:assembleDebug :compat:testDebugUnitTest :compat:lintDebug
python tools/ui_smoke_test.py emulator-5556
python tools/ui_smoke_test.py emulator-5556 com.example.joyvolumeremote.blecompat
```

UI 脚本只用于测试模拟器，会改变测试应用语言/皮肤和模拟器屏幕参数。截图与结果分别保存到 `app/build/ui-review`、`compat/build/ui-review`。单元测试覆盖 HID 报告布局、长属性读取偏移及连续输入队列中按下/松开的顺序、容量和断线清空。

协议依据：[HID over GATT Profile](https://www.bluetooth.com/specifications/specs/hid-over-gatt-profile-1-0/)、[Android GATT Server](https://developer.android.com/reference/android/bluetooth/BluetoothGattServer)、[通知发送回调及串行要求](https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback#onNotificationSent(android.bluetooth.BluetoothDevice,%20int))。此实现是互操作实验原型，未进行 Bluetooth SIG 认证或完整 HOGP 一致性测试。
