#!/bin/sh
set -eu
GRADLE_VERSION="8.7"
ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
CACHE_DIR="$ROOT_DIR/.gradle-local"
DIST_DIR="$CACHE_DIR/gradle-$GRADLE_VERSION"
GRADLE_BIN="$DIST_DIR/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$CACHE_DIR"
  ARCHIVE="$CACHE_DIR/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  echo "Downloading Gradle $GRADLE_VERSION..."
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "Error: curl or wget is required to download Gradle." >&2
    exit 1
  fi
  rm -rf "$CACHE_DIR/gradle-$GRADLE_VERSION" "$DIST_DIR.tmp"
  unzip -q "$ARCHIVE" -d "$CACHE_DIR"
  rm -f "$ARCHIVE"
fi
exec "$GRADLE_BIN" "$@"
