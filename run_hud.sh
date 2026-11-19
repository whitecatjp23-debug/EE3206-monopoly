#!/usr/bin/env bash
set -e
JAVAFX_LIB="${JAVAFX_LIB:-$HOME/javafx-sdk-21/lib}"
BASE="winter_village_javafx/src/main/java"
OUT="out/hud"

mkdir -p "$OUT"

find "$BASE/oopgroup" "$BASE/wintervillage" -name "*.java" > /tmp/hud_sources.txt
javac \
  --module-path "$JAVAFX_LIB" \
  --add-modules javafx.controls,javafx.fxml,javafx.media \
  -d "$OUT" \
  @/tmp/hud_sources.txt

java \
  --module-path "$JAVAFX_LIB" \
  --add-modules javafx.controls,javafx.fxml,javafx.media \
  -cp "$OUT" \
  oopgroup.WinterVillagePlayerHUD
