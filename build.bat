@echo off
cd /d "%~dp0"
set "JAVA_HOME=D:\Program Files\jdk-17.0.2"
call gradlew.bat assembleDebug
pause
