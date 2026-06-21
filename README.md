# CLC12 Capstone — Chess Engine

A bitboard chess engine in Java (JDK 21) with a Swing GUI, a UCI interface,
a Polyglot opening book, and optional Syzygy endgame tablebase support.

## Layout
- `Chess Engine/src/` — all Java source, piece images, and native (Fathom) sources
- `book.bin` — Polyglot opening book (loaded from the working directory)
- Run from the `CLC12Capstone` folder so relative paths resolve.

## Build & run
Compile:  `cd "Chess Engine/src"  &&  javac *.java`
Run the GUI / console / UCI: see `run.bat`, `NewEngine.bat`, `OldEngine.bat`.

## Endgame tablebases (optional)
Build `syzygy.dll` and point the engine at a Syzygy folder — see `README_SYZYGY.md`.
Tablebase files are NOT included in this repo; download a 3–5 piece Syzygy set
and place it in `Chess Engine/src/syzygy/`.