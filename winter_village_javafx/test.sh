#!/usr/bin/env bash
# Compile and run the plain-Java tests for the monopoly model.
# No JUnit is required: the tests use monopoly.test.TestSupport assertions.
set -euo pipefail

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUT="$DIR/out/test-classes"
mkdir -p "$OUT"

echo "Compiling tests..."
SOURCES=()
while IFS= read -r -d '' source; do
  SOURCES+=("$source")
done < <(find "$DIR/src/main/java/monopoly" "$DIR/src/test/java" -name '*.java' -print0)

javac --release 21 -d "$OUT" "${SOURCES[@]}"

echo "Running tests..."
java -cp "$OUT" monopoly.test.AllTests
