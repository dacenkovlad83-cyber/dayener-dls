@echo off
rem Starts the dayener dls launcher (needs Java 25).
set "JAVAW=C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot\bin\javaw.exe"
if not exist "%JAVAW%" set "JAVAW=javaw"
start "" "%JAVAW%" -jar "%~dp0dayener-dls-launcher.jar"
