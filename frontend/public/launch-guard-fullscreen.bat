@echo off
REM Opens the guard kiosk in Edge true fullscreen with no click required.
REM 1. Edit URL below if needed
REM 2. Create a shortcut to this file
REM 3. Place that shortcut in the Windows Startup folder (Win+R -> shell:startup)
REM 4. Uninstall any older "Install this site as an app" shortcut so this launcher is used

set "URL=https://rfidattendance.lpulaguna.com/guard"
set "EDGE=%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"
if not exist "%EDGE%" set "EDGE=%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"

start "" "%EDGE%" --kiosk "%URL%" --edge-kiosk-type=fullscreen --no-first-run --disable-features=TranslateUI
