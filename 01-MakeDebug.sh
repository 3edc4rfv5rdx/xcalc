#!/usr/bin/env bash
set -e

BUILD_FILE="build_number.txt"

if [[ ! -f "$BUILD_FILE" ]]; then
    echo "base_version=0.1" > "$BUILD_FILE"
    echo "build=0" >> "$BUILD_FILE"
    echo "version=0.1.00000000" >> "$BUILD_FILE"
fi

source "$BUILD_FILE"
NEW_BUILD=$((build + 1))
TODAY=$(date +%Y%m%d)
NEW_VERSION="${base_version}.${TODAY}"

cat > "$BUILD_FILE" <<EOF
base_version=${base_version}
build=${NEW_BUILD}
version=${NEW_VERSION}
EOF

echo "Version: $NEW_VERSION  Build: $NEW_BUILD"

./gradlew assembleDebug
