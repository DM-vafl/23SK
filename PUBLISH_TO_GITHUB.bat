@echo off
setlocal
cd /d "%~dp0"
echo ================================================
echo  23SK - upload project to GitHub
echo ================================================
where git >nul 2>nul
if errorlevel 1 (
  echo Git is not installed. Install Git for Windows first:
  echo https://git-scm.com/download/win
  echo Then open this file again.
  pause
  exit /b 1
)
echo.
echo This script uploads the contents of this folder to:
echo https://github.com/DM-vafl/23SK
echo Make sure you created an EMPTY PUBLIC repository named 23SK first.
echo No Telegram token is stored in this package.
echo.
pause
if not exist ".git" git init -b main
if errorlevel 1 goto failed
git config user.name "23SK Cloud Setup"
git config user.email "23sk-cloud-setup@users.noreply.github.com"
git add .
if errorlevel 1 goto failed
git commit -m "Initial 23SK cloud deployment"
if errorlevel 1 (
  echo.
  echo If Git says there is nothing to commit, continue.
)
git branch -M main
git remote get-url origin >nul 2>nul
if errorlevel 1 git remote add origin https://github.com/DM-vafl/23SK.git
if errorlevel 1 goto failed
git push -u origin main
if errorlevel 1 goto failed
echo.
echo Upload complete. Open https://github.com/DM-vafl/23SK
pause
exit /b 0
:failed
echo.
echo Upload did not complete. Read the error above and do not share any credentials in chat.
pause
exit /b 1
