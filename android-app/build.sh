#!/usr/bin/env bash
# بناء NEXA Code 2100 APK يدوياً (من غير Gradle): aapt2 + javac + d8 + apksigner
set -e
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$APP_DIR/build"
KEYSTORE="$HOME/android-debug.keystore"
OUT="${1:-$APP_DIR/NEXA-Code-2100.apk}"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex"

echo "1/6 aapt2: تجميع الموارد وربطها"
"$BT/aapt2" compile --dir "$APP_DIR/res" -o "$BUILD/compiled.zip"
"$BT/aapt2" link -o "$BUILD/unsigned.apk" -I "$AJ" \
    --manifest "$APP_DIR/AndroidManifest.xml" \
    --java "$BUILD/gen" "$BUILD/compiled.zip"

echo "2/6 javac: ترجمة الكود"
find "$APP_DIR/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -cp "$AJ" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "3/6 d8: تحويل لـ DEX"
"$BT/d8" --lib "$AJ" --output "$BUILD/dex" $(find "$BUILD/classes" -name '*.class')

echo "4/6 حزم الـ DEX داخل الـ APK"
(cd "$BUILD" && zip -q -j unsigned.apk dex/classes.dex)

echo "5/6 zipalign"
"$BT/zipalign" -f -p 4 "$BUILD/unsigned.apk" "$BUILD/aligned.apk"

echo "6/6 التوقيع (debug key)"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=Android Debug,O=Alex,C=EG"
fi
"$BT/apksigner" sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT" "$BUILD/aligned.apk"

"$BT/apksigner" verify "$OUT"
echo "✅ APK جاهز: $OUT"
ls -la "$OUT"
