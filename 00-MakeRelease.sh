#!/bin/sh

PROPS_FILE="version.properties"
if [ -f "$PROPS_FILE" ]; then
    N=$(awk -F= '$1=="buildNumber"{print $2}' "$PROPS_FILE")
else
    N=""
fi
N=${N:-0}
NEW=$((N + 1))

if [ -f "$PROPS_FILE" ]; then
    if grep -q '^buildNumber=' "$PROPS_FILE"; then
        sed -i "s/^buildNumber=.*/buildNumber=$NEW/" "$PROPS_FILE"
    else
        printf "buildNumber=%s\n" "$NEW" >> "$PROPS_FILE"
    fi
else
    printf "versionPrefix=0.1\nbuildNumber=%s\n" "$NEW" > "$PROPS_FILE"
fi

./gradlew assembleRelease
