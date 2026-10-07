@echo off
rem Runs the real applet in appletviewer (JDK 8 only).
rem applet.policy lets the sandboxed applet open the JDBC socket to MySQL.
rem Build first with:  mvn package
cd /d "%~dp0"
if not exist target\lib\mysql-connector-j-8.0.33.jar (
    echo Build output not found. Run "mvn package" first.
    exit /b 1
)
if "%JAVA_HOME%"=="" (
    echo JAVA_HOME is not set. Point it at your JDK 8 folder.
    exit /b 1
)
"%JAVA_HOME%\bin\appletviewer" -J-Djava.security.policy=applet.policy MainApplet.html
