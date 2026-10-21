#!/usr/bin/env bash
# Builds simple-tv.apk with the Ubuntu-packaged Android tools:
#   sudo apt-get install aapt apksigner android-sdk-platform-23 dalvik-exchange zipalign
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=/usr/lib/android-sdk/platforms/android-23/android.jar
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes"

aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -I "$ANDROID_JAR" \
    -F "$OUT/unsigned.apk"

javac -source 8 -target 8 -bootclasspath "$ANDROID_JAR" -Xlint:-options \
    -d "$OUT/classes" $(find src "$OUT/gen" -name '*.java')

dalvik-exchange --dex --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && aapt add unsigned.apk classes.dex >/dev/null)

zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# A local signing key, created once. Keep it: updates must be signed with the same key.
KEYSTORE=${KEYSTORE:-simple-tv.keystore}
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -storepass simpletv -keypass simpletv \
        -alias simpletv -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Simple TV"
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:simpletv --key-pass pass:simpletv \
    --min-sdk-version 22 --out simple-tv.apk "$OUT/aligned.apk"
apksigner verify simple-tv.apk
echo "Built $(pwd)/simple-tv.apk"
