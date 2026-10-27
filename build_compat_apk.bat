@echo off
cd /d %~dp0
call gradlew.bat :compat:assembleDebug
if errorlevel 1 exit /b 1
echo APK: compat\build\outputs\apk\debug\FliFli-BLE-Compat-v1.5.4-ble-experimental.2.apk
