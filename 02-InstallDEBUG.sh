#!/bin/sh

apk="app/build/outputs/apk/debug/app-universal-debug.apk"

if [ -z "$apk" ]; then
    echo "No universal release APK found"
    exit 1
fi

echo ">>> Installing: $(basename "$apk")"
adb -s emulator-5554 install -r "$apk"
