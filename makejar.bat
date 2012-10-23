@REM Build the file Grayscale_Granulometry
@REM
@REM Requires the file 'jar.exe' to be in the path
@REM For adding it with windows (Windows Vista)
@REM - Start->Parameters->Configuration Panel->System->
@REM 	Advanced System Parameters->Environement variables
@REM - Clic on Variable 'Path' in 'System Variable' panel
@REM - Add the path to jar.exe, typically:
@REM 	"C:\Program Files\jdk1.6.0_03\bin\"
@REM 	(Adapt version number according to your system)
@REM


@echo off

@REM remove old copy of jar folder if it exists
if exist jar rmdir /S /Q jar

@REM make a copy of the plugins directory into the jar directory
mkdir jar
xcopy plugins jar /s /e
@REM remove '.svn' directories, created by Eclipse from subversion repository
FOR /F "tokens=*" %%i IN ('dir /a:d /s /b jar\*.svn*') DO rmdir /S /Q %%i


:: need to rewrite the plugins configuration file
:: (directly from source to avoid refreshing problems...)
copy src\plugins.config jar\plugins.config /Y


@REM remove old archive if it exists
if exist Grayscale_Granulometry.jar del /f /q Grayscale_Granulometry.jar


@REM build the new archive
jar cf Grayscale_Granulometry.jar -C jar\ .

@REM remove temporary files created during process
if exist jar rmdir /S /Q jar
