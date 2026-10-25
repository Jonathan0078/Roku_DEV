#!/usr/bin/env sh
set -e
if ! command -v gradle >/dev/null 2>&1; then
  echo "Gradle não encontrado no PATH. Abra o projeto no Android Studio/CodeAssist e use Build APK."
  exit 1
fi
gradle assembleDebug
