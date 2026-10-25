@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle nao foi encontrado no PATH. Abra o projeto no Android Studio/CodeAssist e use Build APK.
  pause
  exit /b 1
)
gradle assembleDebug
pause
