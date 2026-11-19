#!/usr/bin/env bash
# Build the standalone Winter Village JavaFX viewer with the bundled SDK.
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

JFX_MODS="javafx.controls,javafx.swing"
mkdir -p "$DIR/out/classes"

echo "Compiling Winter Village viewer..."
SOURCES=()
while IFS= read -r -d '' source; do
  SOURCES+=("$source")
done < <(find "$DIR/src/main/java" -name '*.java' -print0)

javac --release 21 \
  --module-path "$JFX" \
  --add-modules "$JFX_MODS" \
  -cp "$DIR/lib/*" \
  -d "$DIR/out/classes" \
  "${SOURCES[@]}"