@echo off
cd /d "%~dp0..\frontend"
echo Starting PulseMind UI on :5173
call npm run dev
