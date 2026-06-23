@echo off
REM Portable launcher for the NEW engine over UCI (for Cute Chess, Arena, etc.).
REM All paths are relative to this file, so the project can live anywhere.
REM Run build.bat once before first use (and after any code change).
setlocal
set "ROOT=%~dp0"
set "ENG=%ROOT%Chess Engine"
cd /d "%ROOT%"
java -cp "%ENG%\bin" ^
  "-Djava.library.path=%ENG%\src" ^
  "-DsyzygyPath=%ENG%\src\syzygy" ^
  "-Dpolyglot.book=%ROOT%book.bin" ^
  Uci
endlocal
