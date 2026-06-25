#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
./gradlew assembleDebug
printf '\nAPK 输出位置：app/build/outputs/apk/debug/app-debug.apk\n'
