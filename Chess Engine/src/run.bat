@echo off
set SRC=C:\Users\golde\CLC12Capstone\Chess Engine\src
set TB=%SRC%\syzygy
java -Djava.library.path="%SRC%" ^
     -DsyzygyPath="%TB%" ^
     -cp "%SRC%" ^
     %1