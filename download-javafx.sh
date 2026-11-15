#!/usr/bin/env bash
# Download the JavaFX 21 SDK for the current platform into vendor/.
set -euo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION="21.0.8"

case "$(uname -s)-$(uname -m)" in
  Darwin-arm64)  PLAT="osx-aarch64" ;;
  Darwin-x86_64) PLAT="osx-x64" ;;
  Linux-x86_64)  PLAT="linux-x64" ;;
  Linux-aarch64) PLAT="linux-aarch64" ;;
  *) echo "Unsupported platform: $(uname -s)-$(uname -m)" >&2; exit 1 ;;
esac

URL="https://download2.gluonhq.com/openjfx/${VERSION}/openjfx-${VERSION}_${PLAT}_bin-sdk.zip"
mkdir -p "$DIR/vendor"
echo "Downloading $URL"
curl -L -o "$DIR/vendor/openjfx.zip" "$URL"
cd "$DIR/vendor"
unzip -oq openjfx.zip
rm -f openjfx.zip
echo "Installed to $DIR/vendor/javafx-sdk-${VERSION}"
