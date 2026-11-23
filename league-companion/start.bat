@echo off
title Rift Remote
cd /d "%~dp0"
where node >nul 2>nul
if errorlevel 1 (
  echo Node.js fehlt. Bitte installieren: https://nodejs.org  ^(LTS-Version^)
  pause
  exit /b 1
)
node server.js %*
pause
