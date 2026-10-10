@echo off
cd /d %~dp0
call gradlew.bat assembleDebug
if errorlevel 1 exit /b 1
echo.
set "APK_NAME="
for /f "delims=" %%f in ('dir /b /o-d "app\build\outputs\apk\debug\CustomBluetoothInput-v*.apk" 2^>nul') do if not defined APK_NAME set "APK_NAME=%%f"
if not defined APK_NAME (
    echo 未找到带版本号的 APK。
    exit /b 1
)
echo APK 输出位置：app\build\outputs\apk\debug\%APK_NAME%
