#!/usr/bin/env bash
# Sürümü iki yerde birden günceller: gradle.properties (VERSION_NAME) ve Version.kt (SDK_VERSION).
set -euo pipefail
cd "$(dirname "$0")/.."
v="${1:?kullanım: scripts/bump-version.sh 0.2.0}"
sed -i '' "s/^VERSION_NAME=.*/VERSION_NAME=$v/" gradle.properties
sed -i '' "s/SDK_VERSION: String = \".*\"/SDK_VERSION: String = \"$v\"/" bildirim/src/main/kotlin/io/bildirim/sdk/Version.kt
grep -n "VERSION_NAME" gradle.properties; grep -n "SDK_VERSION" bildirim/src/main/kotlin/io/bildirim/sdk/Version.kt
