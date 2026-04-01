#!/usr/bin/env bash
set -e

BUILD_FILE="build_number.txt"

if [[ ! -f "$BUILD_FILE" ]]; then
    echo "base_version=0.1" > "$BUILD_FILE"
    echo "build=0" >> "$BUILD_FILE"
    echo "version=0.1.00000000" >> "$BUILD_FILE"
fi

source "$BUILD_FILE"
echo "Version: $version  Build: $build"
echo "Release build does not change build_number.txt"

./gradlew assembleRelease
