@echo off
setlocal
if "%JAVA_HOME%"=="" set "JAVA_HOME=C:\Program Files\Java\jdk-17.0.11"
set "ROOT=%~dp0.."
if exist "%ROOT%\.env" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%\.env") do (
    if not "%%A"=="" if not "%%A"=="#" set "%%A=%%B"
  )
)
set "PULSEMIND_WATCH_PATH=%ROOT%\sample-service"
set "PULSEMIND_LOG_PATH=%ROOT%\sample-service\logs\application.log"
echo Starting PulseMind engine on :8080
"%ROOT%\.tools\apache-maven-3.9.6\bin\mvn.cmd" -f "%ROOT%\backend\pom.xml" spring-boot:run
