#!/usr/bin/env sh
set -e
cd "$(dirname "$0")"
./gradlew assembleDebug
APK_PATH=$(ls -t app/build/outputs/apk/debug/FliFli-Gesture-HID-v*.apk 2>/dev/null | head -n 1 || true)
if [ -z "$APK_PATH" ]; then
  echo '未找到带版本号的 APK。' >&2
  exit 1
fi
printf '\nAPK 输出位置：%s\n' "$APK_PATH"
