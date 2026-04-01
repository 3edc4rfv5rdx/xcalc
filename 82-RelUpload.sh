#!/usr/bin/env bash
set -e

PROJECT="xcalc"
APK_DIR="app/build/outputs/apk/release"
CHANGELOG_FILE="/tmp/release_notes_$$.md"
DRY_RUN=false

# ------------------------------------------------------------
# Dry-run mode: show what would happen without touching GitHub
# ------------------------------------------------------------
#DRY_RUN=true

# ------------------------------------------------------------
# Upload protection
# ------------------------------------------------------------
UPLOAD_TIMEOUT=180     # seconds per attempt
UPLOAD_RETRY=2         # number of attempts

echo "=== Detecting latest tag ==="
TAG=$(git tag --list 'v*' | sort -V | tail -n 1)

if [[ -z "$TAG" ]]; then
    echo "ERROR: No tags found."
    exit 1
fi

echo "Tag: $TAG"

# ------------------------------------------------------------
# Parse tag: v0.3.20260401+73  ->  VERSION=0.3.20260401  BUILD=73
# ------------------------------------------------------------
CLEAN_TAG="${TAG#v}"
VERSION="${CLEAN_TAG%%+*}"
BUILD="${CLEAN_TAG##*+}"

if [[ -z "$VERSION" || -z "$BUILD" ]]; then
    echo "ERROR: Failed to parse tag: $TAG"
    exit 1
fi

echo "Version: $VERSION"
echo "Build:   $BUILD"

# ------------------------------------------------------------
# Build changelog from CHANGELOG.md
# Collect all sections between current tag and previous tag
# ------------------------------------------------------------
echo "=== Building changelog from CHANGELOG.md ==="

PREV_TAG=$(git tag --list 'v*' | sort -V | grep -B1 "^${TAG}$" | head -1)
if [[ "$PREV_TAG" == "$TAG" ]]; then
    PREV_TAG=""
fi

if [[ -n "$PREV_TAG" ]]; then
    PREV_CLEAN="${PREV_TAG#v}"
    PREV_VER="${PREV_CLEAN%%+*}"
    PREV_BUILD="${PREV_CLEAN##*+}"
    STOP_HEADER="## ${PREV_VER}+${PREV_BUILD}"
    echo "Previous tag: $PREV_TAG (stop at: $STOP_HEADER)"
else
    STOP_HEADER=""
    echo "No previous tag, collecting entire changelog."
fi

awk -v cur="## ${VERSION}+${BUILD}" -v stop_build="$PREV_BUILD" '
    $0 == cur { capture=1; next }
    capture && stop_build != "" && /^## / {
        b = $0; sub(/.*\+/, "", b); sub(/[^0-9].*/, "", b)
        if (b+0 <= stop_build+0) exit
        print ""; print $0; next
    }
    capture { print }
' CHANGELOG.md > "$CHANGELOG_FILE"

echo "Generated changelog:"
echo "--------------------------------------------------"
cat "$CHANGELOG_FILE"
echo "--------------------------------------------------"

# ------------------------------------------------------------
# Find APK files
# ------------------------------------------------------------
APK_ARM64=$(ls -t "$APK_DIR"/*arm64-v8a*.apk 2>/dev/null | head -1)
APK_UNIVERSAL=$(ls -t "$APK_DIR"/*universal*.apk 2>/dev/null | head -1)

echo "=== Checking APK files ==="

for f in "$APK_ARM64" "$APK_UNIVERSAL"; do
    if [[ -z "$f" || ! -f "$f" ]]; then
        echo "ERROR: APK not found in $APK_DIR"
        exit 1
    fi
    echo "OK: $(basename "$f")"
done

# ------------------------------------------------------------
# Target file names in GitHub Release
# ------------------------------------------------------------
DST_ARM64="${PROJECT}-${VERSION}+${BUILD}-arm64-v8a.apk"
DST_UNIVERSAL="${PROJECT}-${VERSION}+${BUILD}-universal.apk"

FILES=(
    "$APK_ARM64#$DST_ARM64"
    "$APK_UNIVERSAL#$DST_UNIVERSAL"
)

# ------------------------------------------------------------
# Create release if not exists
# ------------------------------------------------------------
echo "=== Checking if GitHub Release exists ==="

if $DRY_RUN; then
    echo "[DRY RUN] Would create release: $TAG"
    echo "[DRY RUN] Title: Release $TAG"
    echo "[DRY RUN] Notes file: $CHANGELOG_FILE"
elif gh release view "$TAG" >/dev/null 2>&1; then
    echo "Release already exists."
else
    echo "Creating GitHub Release..."
    gh release create "$TAG" \
        --title "Release $TAG" \
        --notes-file "$CHANGELOG_FILE"
fi

# ------------------------------------------------------------
# Upload helper with retry + timeout + cleanup
# ------------------------------------------------------------
upload_asset() {
    local tag="$1"
    local src="$2"
    local dst="$3"

    echo "--------------------------------------------------"
    echo "Uploading: $(basename "$src") -> $dst"

    if $DRY_RUN; then
        echo "[DRY RUN] Would upload: $(basename "$src") -> $dst"
        return 0
    fi

    for ((i=1; i<=UPLOAD_RETRY; i++)); do
        echo "Attempt $i/$UPLOAD_RETRY..."

        # Remove broken asset if exists (ignore errors)
        gh release delete-asset "$tag" "$dst" -y 2>/dev/null || true

        if timeout "$UPLOAD_TIMEOUT" \
            gh release upload "$tag" "${src}#${dst}" --clobber
        then
            echo "Upload OK: $dst"
            return 0
        fi

        echo "Upload failed or timeout, retrying in 5s..."
        sleep 5
    done

    echo "ERROR: Upload failed after $UPLOAD_RETRY attempts: $dst"
    return 1
}

# ------------------------------------------------------------
# Upload files
# ------------------------------------------------------------
echo "=== Uploading files to Release ==="

for pair in "${FILES[@]}"; do
    SRC="${pair%%#*}"
    DST="${pair##*#}"
    upload_asset "$TAG" "$SRC" "$DST"
done

echo "=== Release upload completed successfully ==="

# ------------------------------------------------------------
# Cleanup
# ------------------------------------------------------------
rm -f "$CHANGELOG_FILE"

sleep 2
