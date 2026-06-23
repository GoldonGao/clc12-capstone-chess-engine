@echo off
REM Portable launcher for the OLD baseline engine over UCI (for engine-vs-engine
REM matches against the new one). The old engine has no tablebase support.
setlocal
set "ROOT=%~dp0"
set "ENG=%ROOT%Chess Engine"
cd /d "%ROOT%"
java -cp "%ENG%\bin" ^
  "-Dpolyglot.book=%ROOT%book.bin" ^
  UciOld
endlocal
