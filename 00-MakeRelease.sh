#!/bin/sh

N=$(cat build_number.txt 2>/dev/null || echo 0)
echo $((N + 1)) > build_number.txt

./gradlew assembleRelease
