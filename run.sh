#!/usr/bin/env bash
# Run the packaged Winter Village Monopoly JavaFX app.
# The JavaFX SDK is detected from the workspace vendor directory, or from
# JAVAFX_LIB when the package is moved elsewhere.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [ -z "${JAVAFX_LIB:-}" ]; then
  for candidate in \
    "$DIR/../vendor/javafx-sdk-21.0.8/lib" \
    "$DIR/vendor/javafx-sdk-21.0.8/lib" \
    "$DIR/winter_village_javafx/vendor/javafx-sdk-21.0.8/lib"; do
    if [ -d "$candidate" ]; then
      JAVAFX_LIB="$candidate"
      break
    fi
  done
fi

if [ -z "${JAVAFX_LIB:-}" ] || [ ! -d "$JAVAFX_LIB" ]; then
  echo "JavaFX SDK not found. Set JAVAFX_LIB to the JavaFX lib directory." >&2
  exit 1
fi

export JAVAFX_LIB
exec "$DIR/winter_village_javafx/run.sh" "$@"
