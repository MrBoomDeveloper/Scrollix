@echo off
rem Builds a production desktop release for Windows. See release-windows.ps1.
rem Must be run on Windows: the desktop module pulls Compose Desktop for the host OS.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0release-windows.ps1" %*
exit /b %ERRORLEVEL%
