@echo off
setlocal enabledelayedexpansion
rem Lightweight Gradle launcher for this sample project.
rem It downloads a local Gradle distribution if Gradle is not already present.
rem This is not the official Gradle wrapper jar; it is a small helper so local Codex can run gradlew.bat assembleDebug.

set BASE_DIR=%~dp0
set GRADLE_VERSION=8.10.2
set GRADLE_DIST_DIR=%BASE_DIR%.gradle-local
set GRADLE_HOME=%GRADLE_DIST_DIR%\gradle-%GRADLE_VERSION%
set GRADLE_BIN=%GRADLE_HOME%\bin\gradle.bat

if not exist "%GRADLE_BIN%" (
  if not exist "%GRADLE_DIST_DIR%" mkdir "%GRADLE_DIST_DIR%"
  set ZIP=%GRADLE_DIST_DIR%\gradle-%GRADLE_VERSION%-bin.zip
  set URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip
  echo Gradle %GRADLE_VERSION% not found locally. Downloading from !URL!
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $wc=New-Object Net.WebClient; $wc.DownloadFile($env:URL, $env:ZIP)"
  tar -xf "!ZIP!" -C "!GRADLE_DIST_DIR!"
  if errorlevel 1 (
    echo Gradle 下载或解压失败。也可以安装 Android Studio 后用它构建。
    exit /b 1
  )
)

call "%GRADLE_BIN%" %*
