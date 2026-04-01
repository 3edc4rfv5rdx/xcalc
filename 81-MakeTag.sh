#!/usr/bin/env bash
set -e

BUILD_FILE="build_number.txt"

echo "=== Checking that the working tree is clean ==="

if ! git diff --quiet || ! git diff --cached --quiet; then
    echo "ERROR: You have uncommitted changes."
    echo "Please commit or stash them before running this script."
    exit 1
fi

echo "OK: Working tree is clean."

echo "=== Reading build info ==="

if [[ ! -f "$BUILD_FILE" ]]; then
    echo "ERROR: $BUILD_FILE not found."
    exit 1
fi

source "$BUILD_FILE"

if [[ -z "$version" || -z "$build" ]]; then
    echo "ERROR: Failed to read version/build from $BUILD_FILE"
    exit 1
fi

TAG="v${version}+${build}"

echo "Version: $version"
echo "Build:   $build"
echo "Tag:     $TAG"

# Check if tag already exists
if git tag --list "$TAG" | grep -q "$TAG"; then
    echo "Tag $TAG already exists. Nothing to do."
    exit 0
fi

echo "=== Creating tag $TAG ==="
git tag -a "$TAG" -m "Build $build"

echo "=== Done: tag $TAG created ==="

sleep 2
