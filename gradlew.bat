@rem ##########################################################################
@rem  Gradle startup script for Windows
@rem ##########################################################################

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

@rem ##########################################################################

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

@rem ##########################################################################

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% neq 0 goto javaNotFound

goto execute

:javaNotFound
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo Please set the JAVA_HOME variable in your environment to match the location of your Java installation.
goto error

:execute
set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

"%JAVA_EXE%" -Dorg.gradle.appname=gradlew -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*

:error
exit /b 1
