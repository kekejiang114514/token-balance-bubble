#!/bin/sh
# 下载构建所需的外部依赖到 $DEPS（默认 .build/）。
# 这些文件体积较大，不纳入版本库。
set -e

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEPS=${DEPS:-$ROOT/.build}
mkdir -p "$DEPS"
cd "$DEPS"

if [ ! -f android.jar ]; then
    echo "下载 android.jar (platform 29) ..."
    curl -fL -o platform29.zip \
        https://dl.google.com/android/repository/platform-29_r05.zip
    unzip -o -q platform29.zip 'android-10/android.jar'
    mv android-10/android.jar android.jar
    rm -rf platform29.zip android-10
fi

R8_VER=9.4.24
if [ ! -f r8.jar ]; then
    echo "下载 r8.jar ..."
    curl -fL -o r8.jar \
        "https://dl.google.com/dl/android/maven2/com/android/tools/r8/$R8_VER/r8-$R8_VER.jar"
fi

if [ ! -f apksigner.jar ]; then
    echo "下载 apksigner.jar ..."
    # apksigner 来自 Android SDK build-tools，这里从 Google 的 Maven 仓库取
    curl -fL -o apksigner.jar \
        "https://dl.google.com/dl/android/maven2/com/android/tools/build/apksigner/30.4.2/apksigner-30.4.2.jar" || {
        echo "自动下载失败。可以手动从 Android SDK build-tools 里拷贝："
        echo "  \$ANDROID_HOME/build-tools/<版本>/lib/apksigner.jar"
        exit 1
    }
fi

ls -la "$DEPS"
echo "依赖就绪：$DEPS"
