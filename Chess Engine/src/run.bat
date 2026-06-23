@echo off
REM Portable generic launcher:  run.bat <MainClass> [args...]
REM   run.bat GUI            run.bat Main            run.bat Uci
REM   run.bat SyzygyTest     (uses the syzygy folder below automatically)
REM
REM Lives in "Chess Engine\src". All paths are derived from this file's location,
REM so the project can sit in any folder. Run build.bat (at the repo root) once
REM before using this, and again after any code change.
setlocal
REM --- this file's folder (the src directory), minus the trailing backslash
set "SRC=%~dp0"
set "SRC=%SRC:~0,-1%"
REM --- walk up: ENG = "Chess Engine", ROOT = repo root (holds book.bin)
for %%I in ("%SRC%\..")  do set "ENG=%%~fI"
for %%I in ("%ENG%\..")  do set "ROOT=%%~fI"
set "BIN=%ENG%\bin"
set "TB=%SRC%\syzygy"
 
cd /d "%ROOT%"
java -cp "%BIN%;%SRC%" ^
     "-Djava.library.path=%SRC%" ^
     "-DsyzygyPath=%TB%" ^
     "-Dpolyglot.book=%ROOT%\book.bin" ^
     %*
endlocal
 