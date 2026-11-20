@echo off
cd /d "%~dp0"
where py >nul 2>nul
if errorlevel 1 (
  python tools\get_chat_ids.py
) else (
  py -3 tools\get_chat_ids.py
)
echo.
pause
