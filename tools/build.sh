#!/bin/sh
# 从源码构建 token 余额查询器 APK。
#
# 用法：
#   sh tools/build.sh                     # 使用默认外部依赖目录 .build/
#   DEPS=/path/to/deps sh tools/build.sh  # 指定外部依赖目录
#
# 需要的外部依赖（体积较大，未纳入仓库，见 tools/fetch-deps.sh）：
#   $DEPS/android.jar     —— Android SDK platform 29 的 android.jar
#   $DEPS/r8.jar          —— R8/D8 编译器
#   $DEPS/apksigner.jar   —— apksigner
#
# 还需要 JDK 17（javac 与 java）以及 python3。
set -e

unset LD_LIBRARY_PATH PREFIX

ROOT=$(cd "$(dirname "$0")/.." && pwd)
DEPS=${DEPS:-$ROOT/.build}
OUT=$ROOT/out

JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}
export JAVA_HOME
PATH=$JAVA_HOME/bin:$PATH
export PATH

AJ=$DEPS/android.jar
ASJ=$DEPS/apksigner.jar

for f in "$AJ" "$ASJ" "$DEPS/r8.jar"; do
    if [ ! -f "$f" ]; then
        echo "缺少依赖：$f"
        echo "先运行 sh tools/fetch-deps.sh 下载，或用 DEPS=/your/deps 指定目录。"
        exit 1
    fi
done

KS=${KS:-$ROOT/keys/release.p12}
if [ ! -f "$KS" ]; then
    echo "缺少签名密钥：$KS"
    echo "自己生成一个："
    echo "  keytool -genkeypair -keystore keys/release.p12 -storetype PKCS12 \\"
    echo "          -alias release -keyalg RSA -keysize 2048 -validity 10000"
    echo "口令放进 keys/storepass.txt（该目录已被 .gitignore 忽略）。"
    exit 1
fi

cd "$ROOT"
mkdir -p out

echo "[1/6] javac"
rm -rf out/classes && mkdir -p out/classes
javac -nowarn -encoding UTF-8 -source 8 -target 8 -bootclasspath "$AJ" \
    -d out/classes $(find app/src -name '*.java')

echo "[2/6] d8"
rm -rf out/dex && mkdir -p out/dex
java -cp "$DEPS/r8.jar" com.android.tools.r8.D8 --min-api 23 --lib "$AJ" \
    --output out/dex $(find out/classes -name '*.class')

echo "[3/6] resources.arsc"
python3 tools/arsc_build.py out/resources.arsc

echo "[4/6] AndroidManifest.xml"
python3 tools/manifest_gen.py out/AndroidManifest.xml

echo "[5/6] package"
python3 tools/pack_apk.py out/unsigned.apk \
    z:AndroidManifest.xml=out/AndroidManifest.xml \
    z:classes.dex=out/dex/classes.dex \
    s:resources.arsc=out/resources.arsc \
    s:res/mipmap/ic_launcher.png=app/res/mipmap/ic_launcher.png \
    s:assets/char.png=app/assets/char.png

echo "[6/6] sign"
STORE_PASS=$(cat keys/storepass.txt)
export STORE_PASS
java -jar "$ASJ" sign --ks "$KS" --ks-pass env:STORE_PASS \
    --ks-key-alias release --key-pass env:STORE_PASS \
    --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true \
    --min-sdk-version 23 --out out/app.apk out/unsigned.apk
unset STORE_PASS

java -jar "$ASJ" verify --verbose out/app.apk | grep -E "Verified using v[123] scheme" || true
ls -la out/app.apk
echo "BUILD OK -> out/app.apk"
