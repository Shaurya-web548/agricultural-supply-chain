@echo off
rem Desktop launcher: runs MainApplet.main(), which hosts the applet in a window.
rem Build first with:  mvn package
cd /d "%~dp0"
if not exist target\agricultural-supply-chain-1.0.0.jar (
    echo Jar not found. Run "mvn package" first.
    exit /b 1
)
java -jar target\agricultural-supply-chain-1.0.0.jar
