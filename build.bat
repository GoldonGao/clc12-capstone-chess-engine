@echo off
REM Compiles all Java sources into "Chess Engine\bin". Run this once now, and
REM again any time you change the code. Paths are relative to this file's
REM location, so the project can live in any folder.
setlocal
set "ROOT=%~dp0"
set "SRC=%ROOT%Chess Engine\src"
set "BIN=%ROOT%Chess Engine\bin"

if not exist "%BIN%" mkdir "%BIN%"

echo Compiling Java sources from "%SRC%" ...
pushd "%SRC%"
dir /b *.java > "%TEMP%\chess_sources.txt"
javac -d "%BIN%" @"%TEMP%\chess_sources.txt"
set "RC=%ERRORLEVEL%"
del "%TEMP%\chess_sources.txt"
popd

echo.
if "%RC%"=="0" (
  echo Build succeeded. Compiled classes are in:
  echo   %BIN%
) else (
  echo *** BUILD FAILED -- see the messages above ***
)
endlocal
pause
