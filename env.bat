@echo off
rem =====================================================================
rem Session environment for building/running the project (Windows).
rem Usage:  call env.bat && mvn compile
rem You can also add these paths permanently via System Properties ->
rem Environment Variables instead of using this file.
rem =====================================================================
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot"
set "M2_HOME=c:\Users\shaur\Downloads\mp\oops-project\tools\apache-maven-3.9.9"
set "PATH=%JAVA_HOME%\bin;%M2_HOME%\bin;C:\Program Files\MySQL\MySQL Server 8.0\bin;%PATH%"
