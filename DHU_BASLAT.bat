@echo off
title Android Auto Desktop Head Unit
echo ========================================================
echo   Android Auto Simulatoru (DHU) Baslatiliyor...
echo ========================================================
echo.
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" forward tcp:5277 tcp:5277
"%LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe"
pause
