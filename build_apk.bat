@echo off
cd /d %~dp0
call gradlew.bat assembleDebug
if errorlevel 1 exit /b 1
echo.
echo APK 输出位置：app\build\outputs\apk\debug\app-debug.apk
