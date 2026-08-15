@echo off
set JAVA_HOME=G:\jdk-25.0.2
set PATH=%JAVA_HOME%\bin;%PATH%
call "E:\Gradle\gradle-9.7.0\bin\gradle.bat" %*
