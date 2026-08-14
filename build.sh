#!/usr/bin/env bash
# Manual Android build: aapt2 -> javac -> d8 -> jar -> zipalign -> apksigner.
# Run from the repository root.
set -euo pipefail

BT="/c/Users/JHCWColin/AppData/Local/Android/Sdk/build-tools/36.0.0"
ANDROID_JAR="/c/Users/JHCWColin/AppData/Local/Android/Sdk/platforms/android-36/android.jar"
JBR="/d/AndroidStudio/jbr"
AAPT2="$BT/aapt2.exe"
JAVAC="$JBR/bin/javac.exe"
JAVA="$JBR/bin/java.exe"
JAR="$JBR/bin/jar.exe"
KEYTOOL="$JBR/bin/keytool.exe"
D8="$BT/lib/d8.jar"
APKSIGNER="$BT/lib/apksigner.jar"
ZIPALIGN="$BT/zipalign.exe"

APP="app"
BUILD="build"
DIST="dist"
KEYSTORE="release.jks"
ALIAS="musicliveson"
STOREPASS="12345678"
KEYPASS="12345678"
OUT="$DIST/TheMusicLivesOn8-v1.0.apk"

rm -rf "$BUILD" "$DIST"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$DIST"

echo "==> aapt2 compile"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "==> aapt2 link"
"$AAPT2" link -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  --java "$BUILD/gen" \
  -o "$BUILD/base.apk" \
  --min-sdk-version 27 --target-sdk-version 34 --auto-add-overlay

echo "==> javac"
SRCS=$(find "$BUILD/gen" "$APP/src" -name "*.java")
"$JAVAC" -encoding UTF-8 -source 1.8 -target 1.8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" -d "$BUILD/classes" $SRCS

echo "==> jar classes"
"$JAR" cf "$BUILD/classes.jar" -C "$BUILD/classes" .

echo "==> d8"
"$JAVA" -cp "$D8" com.android.tools.r8.D8 --release --lib "$ANDROID_JAR" \
  --min-api 27 --output "$BUILD/dex" "$BUILD/classes.jar"

echo "==> add dex to apk"
"$JAR" uf "$BUILD/base.apk" -C "$BUILD/dex" classes.dex

echo "==> zipalign"
"$ZIPALIGN" -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  echo "==> generate keystore"
  "$KEYTOOL" -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STOREPASS" -keypass "$KEYPASS" \
    -dname "CN=The Music Lives On 8, OU=JHCWColin, O=JHCWColin, C=CN" 2>&1 | tail -3
fi

echo "==> sign"
"$JAVA" -jar "$APKSIGNER" sign --ks "$KEYSTORE" \
  --ks-pass "pass:$STOREPASS" --key-pass "pass:$KEYPASS" --ks-key-alias "$ALIAS" \
  --out "$OUT" "$BUILD/aligned.apk"

echo "==> verify"
"$JAVA" -jar "$APKSIGNER" verify --print-certs "$OUT"

echo "==> badging"
"$AAPT2" dump badging "$OUT" | head -20

echo "==> DONE"
ls -la "$DIST"
