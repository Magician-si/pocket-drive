#!/usr/bin/env bash
# Pocket Drive manual build (no Gradle in this sandbox).
# Usage: ./build.sh phone|tv|both
# Pipeline: keytool (local-only TLS cert + debug keystore, both gitignored)
#   -> aapt2 compile+link -> javac -> jar -> d8 -> zip dex -> zipalign -> apksigner
# The TLS .p12 is generated fresh at build time and NEVER committed:
# the code loads assets/pocketdrive_tls.p12 with alias/password "pocketdrive".
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="$HOME/workspace/.jdk/jdk-17.0.20.1+1"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="$HOME/workspace/.android-sdk"
BT="$SDK/build-tools/34.0.0"
AJAR="$SDK/platforms/android-34/android.jar"
SRC="app/src/main"
BUILD="build"
mkdir -p "$BUILD/assets" "$BUILD/out"

# 1. Local-only signing key (debug) and TLS cert. Both stay out of git.
if [ ! -f "$BUILD/debug.keystore" ]; then
  keytool -genkeypair -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 3650 \
    -keystore "$BUILD/debug.keystore" -storepass android -keypass android \
    -dname "CN=Android Debug, O=Android, C=US" >/dev/null 2>&1
fi
if [ ! -f "$BUILD/assets/pocketdrive_tls.p12" ]; then
  keytool -genkeypair -alias pocketdrive -keyalg RSA -keysize 2048 -validity 3650 \
    -keystore "$BUILD/assets/pocketdrive_tls.p12" -storetype PKCS12 \
    -storepass pocketdrive -keypass pocketdrive \
    -dname "CN=PocketDrive.local, OU=Local, O=PocketDrive, C=ID" >/dev/null 2>&1
fi

build_one() {
  local variant="$1"   # phone | tv
  local out="$BUILD/out-$variant"
  rm -rf "$out" && mkdir -p "$out"
  cp -r "$SRC/res" "$out/res"
  mkdir -p "$out/assets"
  cp "$BUILD/assets/pocketdrive_tls.p12" "$out/assets/"
  # aapt2 needs assets inside the res dir tree? No: link takes --assets.
  "$BT/aapt2" compile --dir "$out/res" -o "$out/compiled.zip"
  "$BT/aapt2" link -o "$out/app.apk" -I "$AJAR" \
    --manifest "$SRC/AndroidManifest-$variant.xml" \
    --java "$out/gen" -A "$out/assets" \
    --min-sdk-version 26 --target-sdk-version 34 \
    --version-code 1 --version-name 1.0 \
    "$out/compiled.zip"
  mkdir -p "$out/classes"
  find "$SRC/java" -name '*.java' > "$out/sources.txt"
  find "$out/gen" -name 'R.java' >> "$out/sources.txt"
  javac -encoding UTF-8 -cp "$AJAR" -d "$out/classes" @"$out/sources.txt"
  local abs_out="$(pwd)/$out"
  (cd "$out/classes" && jar cf "$abs_out/app-classes.jar" .)
  mkdir -p "$out/dex"
  "$BT/d8" --lib "$AJAR" --min-api 26 --output "$out/dex" "$out/app-classes.jar"
  cp "$out/app.apk" "$out/app-unaligned.apk"
  (cd "$out/dex" && zip -q "$abs_out/app-unaligned.apk" classes.dex)
  "$BT/zipalign" -f 4 "$out/app-unaligned.apk" "$out/app-aligned.apk"
  mkdir -p dist
  "$BT/apksigner" sign --ks "$BUILD/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "dist/pocketdrive-$variant.apk" "$out/app-aligned.apk"
  ls -la "dist/pocketdrive-$variant.apk"
}

case "${1:-both}" in
  phone) build_one phone ;;
  tv) build_one tv ;;
  both) build_one phone; build_one tv ;;
  *) echo "usage: $0 phone|tv|both" >&2; exit 2 ;;
esac
