@echo off
rem Run XsdViewer with the bundled JRE. Arguments are passed to the tool:
rem   xsdviewer.bat [--port N] [--host H] [--no-browser] [file.xsd]
"%~dp0jre\bin\java.exe" -jar "%~dp0xsdviewer.jar" %*
