#!/bin/sh

apk=$(ls -t app/build/outputs/apk/release/*-universal.apk 2>/dev/null | head -1)

if [ -z "$apk" ]; then
    echo "No universal release APK found"
    exit 1
fi

echo ">>> Installing: $(basename "$apk")"
adb -s RFCW91FV79X install -r "$apk"
