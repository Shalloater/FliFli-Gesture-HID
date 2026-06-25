#!/usr/bin/env sh
# Lightweight Gradle launcher for this sample project.
# It downloads a local Gradle distribution if Gradle is not already present.
# This is not the official Gradle wrapper jar; it is a small helper so local Codex can run ./gradlew assembleDebug.

set -e

BASE_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GRADLE_VERSION=${GRADLE_VERSION:-8.10.2}
GRADLE_DIST_DIR="$BASE_DIR/.gradle-local"
GRADLE_HOME="$GRADLE_DIST_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$GRADLE_DIST_DIR"
  ZIP="$GRADLE_DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  echo "Gradle $GRADLE_VERSION not found locally. Downloading from $URL"
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail -o "$ZIP" "$URL"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ZIP" "$URL"
  else
    echo "需要 curl 或 wget 下载 Gradle。也可以安装 Android Studio 后用它构建。" >&2
    exit 1
  fi
  unzip -q "$ZIP" -d "$GRADLE_DIST_DIR"
fi

exec "$GRADLE_BIN" "$@"
