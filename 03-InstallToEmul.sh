#!/bin/sh

dat=$(date +%Y%m%d)
build="$(tr -d '[:space:]' < build_number.txt)"

echo ">>>"${dat}+${build}
adb -s emulator-5554 install -r \
  app/build/outputs/apk/release/xcalc-0.1.${dat}+${build}-release-universal.apk
