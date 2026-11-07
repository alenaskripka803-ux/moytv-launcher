#!/bin/bash
# Builds MoyTV.apk without Gradle: aapt -> javac -> dx -> zipalign -> apksigner
set -euo pipefail
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
rm -rf build && mkdir -p build/gen build/classes

aapt package -f -m -M AndroidManifest.xml -S res -A assets -I "$SDK" \
  -J build/gen -F build/unsigned.apk --min-sdk-version 23 --target-sdk-version 28 \
  --version-code 3 --version-name 1.2 -0 otf

javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$SDK" \
  -d build/classes $(find src build/gen -name '*.java')

dalvik-exchange --dex --min-sdk-version=23 --output=build/classes.dex build/classes
(cd build && aapt add unsigned.apk classes.dex >/dev/null)

zipalign -f -p 4 build/unsigned.apk build/aligned.apk

if [ ! -f moytv.keystore ]; then
  keytool -genkeypair -keystore moytv.keystore -storepass moytvpass -keypass moytvpass \
    -alias moytv -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Moy TV" >/dev/null 2>&1
fi
apksigner sign --ks moytv.keystore --ks-pass pass:moytvpass --key-pass pass:moytvpass \
  --out build/MoyTV.apk build/aligned.apk
apksigner verify -v build/MoyTV.apk | head -5
ls -la build/MoyTV.apk
