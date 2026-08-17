#!/usr/bin/env bash
# Build "The Lyric Lives On 8" (shared-library client) into dist/.
# Run from the repository root. Uses the SAME release.jks as build-music.sh so
# both apps share a signing key (required for the shared-library permission).
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

APP="lyric-app"
BUILD="build-lyric"
DIST="dist"
KEYSTORE="release.jks"
ALIAS="musicliveson"
STOREPASS="12345678"
KEYPASS="12345678"
OUT="$DIST/TheLyricLivesOn8-v1.0.apk"

rm -rf "$BUILD"
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dex" "$DIST"

echo "==> [lyric] aapt2 compile"
"$AAPT2" compile --dir "$APP/res" -o "$BUILD/res.zip"

echo "==> [lyric] aapt2 link"
"$AAPT2" link -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -R "$BUILD/res.zip" \
  --java "$BUILD/gen" \
  -o "$BUILD/base.apk" \
  --min-sdk-version 27 --target-sdk-version 34 --auto-add-overlay

echo "==> [lyric] javac"
SRCS=$(find "$BUILD/gen" "$APP/src" -name "*.java")
"$JAVAC" -encoding UTF-8 -source 1.8 -target 1.8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" -d "$BUILD/classes" $SRCS

echo "==> [lyric] jar classes"
"$JAR" cf "$BUILD/classes.jar" -C "$BUILD/classes" .

echo "==> [lyric] d8"
"$JAVA" -cp "$D8" com.android.tools.r8.D8 --release --lib "$ANDROID_JAR" \
  --min-api 27 --output "$BUILD/dex" "$BUILD/classes.jar"

echo "==> [lyric] add dex to apk"
"$JAR" uf "$BUILD/base.apk" -C "$BUILD/dex" classes.dex

echo "==> [lyric] zipalign"
"$ZIPALIGN" -f 4 "$BUILD/base.apk" "$BUILD/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
  echo "==> [lyric] generate keystore (MUST match build-music.sh)"
  "$KEYTOOL" -genkeypair -v -keystore "$KEYSTORE" -alias "$ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$STOREPASS" -keypass "$KEYPASS" \
    -dname "CN=The Lyric Lives On 8, OU=JHCWColin, O=JHCWColin, C=CN" 2>&1 | tail -3
fi

echo "==> [lyric] sign"
"$JAVA" -jar "$APKSIGNER" sign --ks "$KEYSTORE" \
  --ks-pass "pass:$STOREPASS" --key-pass "pass:$KEYPASS" --ks-key-alias "$ALIAS" \
  --min-sdk-version 27 \
  --out "$OUT" "$BUILD/aligned.apk"

echo "==> [lyric] verify"
"$JAVA" -jar "$APKSIGNER" verify --print-certs "$OUT"
echo "==> [lyric] DONE: $OUT"
