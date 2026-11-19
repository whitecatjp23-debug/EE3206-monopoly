#!/usr/bin/env bash
# Run the standalone Winter Village JavaFX 3D viewer.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JFX="${JAVAFX_LIB:-$DIR/../vendor/javafx-sdk-21.0.8/lib}"
if [ ! -d "$JFX" ]; then
  JFX="$DIR/vendor/javafx-sdk-21.0.8/lib"
fi
if [ ! -d "$JFX" ]; then
  echo "JavaFX SDK not found. Set JAVAFX_LIB to the JavaFX lib directory." >&2
  exit 1
fi

if [ ! -f "$DIR/out/classes/wintervillage/WinterVillageApp.class" ]; then
  "$DIR/build.sh"
fi

PROFILE="${WINTER_VILLAGE_PROFILE:-v2}"
ASSET="$DIR/assets/winter_village_sample_${PROFILE}.glb"
if [ ! -f "$ASSET" ]; then
  ASSET="$DIR/assets/winter_village_sample.glb"
fi

exec java \
  --module-path "$JFX" --add-modules javafx.controls,javafx.swing \
  -Dwinter.village.profile="$PROFILE" \
  -Dwinter.village.asset="$ASSET" \
  -Dwinter.village.tokens.root="$DIR/assets/tokens" \
  -Dwinter.village.dice.root="$DIR/assets/dice" \
  -cp "$DIR/out/classes:$DIR/lib/*" \
  wintervillage.WinterVillageApp "$@"