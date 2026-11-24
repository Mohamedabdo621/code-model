#!/usr/bin/env bash
# بناء NEXA Code 2100 APK يدوياً: llama.cpp (NDK) + aapt2 + javac + d8 + apksigner
set -e
export JAVA_HOME="${JAVA_HOME:-$HOME/jdk17}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-$HOME/android-sdk}"
NDK="${ANDROID_NDK_HOME:-$HOME/android-ndk}"
BT="$SDK/build-tools/34.0.0"
AJ="$SDK/platforms/android-34/android.jar"
APP_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD="$APP_DIR/build"
KEYSTORE="$HOME/android-debug.keystore"
OUT="${1:-$APP_DIR/NEXA-Code-2100.apk}"
ABIS="arm64-v8a"

echo "0/7 بناء المكتبات الأصلية (llama.cpp + JNI) لكل المعماريات"
for ABI in $ABIS; do
    NATIVE_BUILD="$APP_DIR/jni/build-$ABI"
    if [ ! -f "$NATIVE_BUILD/libnexa_llama.so" ]; then
        cmake -S "$APP_DIR/jni" -B "$NATIVE_BUILD" \
            -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
            -DANDROID_ABI="$ABI" -DANDROID_PLATFORM=android-24 \
            -DCMAKE_BUILD_TYPE=Release
        cmake --build "$NATIVE_BUILD" -j 3
    fi
    echo "$ABI:"; ls "$NATIVE_BUILD"/*.so "$NATIVE_BUILD"/bin/*.so 2>/dev/null
done

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$BUILD/pkg/lib"
for ABI in $ABIS; do mkdir -p "$BUILD/pkg/lib/$ABI"; done

echo "1/7 aapt2: تجميع الموارد وربطها"
"$BT/aapt2" compile --dir "$APP_DIR/res" -o "$BUILD/compiled.zip"
"$BT/aapt2" link -o "$BUILD/unsigned.apk" -I "$AJ" \
    --manifest "$APP_DIR/AndroidManifest.xml" \
    --java "$BUILD/gen" "$BUILD/compiled.zip"

echo "2/7 javac: ترجمة الكود"
find "$APP_DIR/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
javac -encoding UTF-8 -cp "$AJ" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "3/7 d8: تحويل لـ DEX"
"$BT/d8" --lib "$AJ" --output "$BUILD/dex" $(find "$BUILD/classes" -name '*.class')

echo "4/7 حزم الـ DEX والمكتبات الأصلية داخل الـ APK"
for ABI in $ABIS; do
    find "$APP_DIR/jni/build-$ABI" -maxdepth 2 -name '*.so' -exec cp {} "$BUILD/pkg/lib/$ABI/" \;
    # libggml-cpu بيعتمد على libomp (OpenMP) — لازم يتحزم مع التطبيق
    cp "$NDK/toolchains/llvm/prebuilt/linux-x86_64/lib/clang/18/lib/linux/aarch64/libomp.so" "$BUILD/pkg/lib/$ABI/"
    echo "$ABI libs:"; ls "$BUILD/pkg/lib/$ABI/"
done
cp "$BUILD/dex/classes.dex" "$BUILD/pkg/"
cp "$BUILD/unsigned.apk" "$BUILD/pkg.apk"
(cd "$BUILD/pkg" && zip -q -r "$BUILD/pkg.apk" classes.dex lib)

echo "5/7 zipalign"
"$BT/zipalign" -f -p 4 "$BUILD/pkg.apk" "$BUILD/aligned.apk"

echo "6/7 التوقيع (debug key)"
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
