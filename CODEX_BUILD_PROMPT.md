请在这个 Android 项目中完成检查、修复并构建 APK：

1. 这是一个 Android 蓝牙 HID 自定义输入设备项目，公开项目名为 `FliFli Gesture HID`，中文产品名为“翻翻 · 蓝牙手势键盘”，App 显示名称为“翻翻”，APK 文件名格式为 `FliFli-v版本号.apk`。
2. 连接前显示已配对设备和连接页；连接成功后，用户可在“键盘”页选择并运行自定义键盘。
3. 当前支持两类键盘：全屏手势键盘和电脑键盘。全屏手势键盘支持单指、双指、三指点按和滑动手势，以及单指顺时针/逆时针画圈。
4. 动作输出包括键盘按键、组合键和 Consumer Control 媒体键；每个动作都必须发送必要的松开报告，避免目标设备认为按键一直按住。
5. 当前不提供 Gamepad/手柄 HID 输出，也不要恢复已移除的手柄报告描述符或相关动作。
6. 目标设备必须是支持蓝牙 HID 主机的电脑、电视、电视盒子或平板等设备。
7. 请检查 AndroidManifest.xml、build.gradle.kts、MainActivity.java 及相关 HID/手势代码是否能编译。
8. 如有编译错误，请直接修复；不要添加与当前需求无关的功能或依赖。
9. 执行 `./gradlew test assembleDebug`（Windows 可使用 `gradlew.bat test assembleDebug`）。
10. 构建成功后确认 APK 路径：`app/build/outputs/apk/debug/FliFli-v1.5.4.apk`。
