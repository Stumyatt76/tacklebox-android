@rem Gradle startup script for Windows
@echo off
set DIRNAME=%~dp0
java %JAVA_OPTS% %GRADLE_OPTS% -classpath "%DIRNAME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
