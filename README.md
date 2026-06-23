# CLC12 Capstone — Chess Engine

A fully functional chess engine written in Java (JDK 21), built from scratch as a
capstone project. It plays legal, strategic chess and can be run in three modes: a
graphical Swing interface, an interactive console, and a UCI-protocol interface
compatible with professional chess GUIs such as Cute Chess and Arena.

---

## Features

### Board representation
- **Bitboards** — the board is stored as a set of 64-bit integers (one per piece
  type per colour), allowing attack and move masks to be computed with single CPU
  instructions rather than loops
- **Incremental Zobrist hashing** — a unique 64-bit fingerprint is maintained for
  every position, updated on each move and unmove, used by the transposition table
  and the opening book
- **Full move generation** — handles all chess rules including en passant, castling,
  promotion, pins, double check, and stalemate; verified by perft testing against
  published node counts (startpos depth 5 = 4,865,609; Kiwipete depth 3 = 97,862)

### Search
- **Iterative deepening minimax** with alpha-beta pruning
- **Aspiration windows** — each new depth searches a narrow score band around the
  previous result, widening only on fail
- **Principal Variation Search (PVS)** — searches later moves with a null window
  first; re-searches only when they unexpectedly improve alpha
- **Null-move pruning** — skips a turn and searches at reduced depth; if the
  position is still good without moving, prunes the branch
- **Late Move Reduction (LMR)** — searches moves ordered late in the list to a
  shallower depth, saving time for the moves that matter
- **Quiescence search** — extends the search on captures to avoid horizon-effect
  blunders caused by cutting off in the middle of an exchange
- **Transposition table** — caches previously searched positions across the entire
  session; also used in quiescence search
- **Killer moves** — remembers quiet moves that caused a beta cutoff at a given ply
  and tries them first at that ply in sibling nodes
- **History heuristic** — accumulates a score for each from-to pair across the search
  to improve quiet move ordering
- **Repetition penalty** — discourages repeating positions to avoid draws in winning
  endgames

### Evaluation
- **Tapered PeSTO piece-square tables** — smoothly interpolates between separate
  middlegame and endgame tables as material comes off the board, using a phase
  counter based on remaining piece types
- **Pawn structure** — penalises doubled, isolated, and backward pawns; rewards
  passed pawns
- **Mobility** — counts reachable squares per piece type
- **King safety** — evaluates pawn shelter and exposure to sliding piece attacks
- **Bishop pair bonus** — rewards keeping both bishops in open positions
- **Rook activity** — rewards rooks on open and semi-open files, and on the
  seventh rank

### Opening book
- Reads a standard **Polyglot** format opening book (`book.bin`) keyed by Zobrist
  hash; lookups are instant and require no search
- The book path is configurable via `-Dpolyglot.book=` or the `polyglot.book`
  system property; falls back to `book.bin` in the working directory

### Endgame tablebases
- Integrates **Syzygy** endgame tablebases via the Fathom probing library (JNI
  bridge to a compiled native DLL)
- WDL (Win/Draw/Loss) probing in the search tree — any covered position (≤ max
  pieces, no castling rights) is immediately returned as a perfect score without
  searching further
- DTZ (Distance to Zeroing) probing at the root — the engine selects the move that
  converts optimally while respecting the 50-move rule
- Entirely optional and gracefully disabled when the library or files are absent
- See [README_SYZYGY.md](README_SYZYGY.md) for full details

### Interfaces
- **GUI** (`GUI.java`) — Swing graphical board with piece images, time controls
  (Untimed, 1 min, 1|1, 3 min, 3|2, 5 min, 5|2, 10 min, 15|10), an engine-vs-engine
  match panel, and display modes
- **Console** (`Main.java`) — interactive text-based play and testing
- **UCI** (`Uci.java`) — full Universal Chess Interface protocol implementation;
  compatible with Cute Chess, Arena, and any UCI-capable GUI; advertises
  `SyzygyPath` and `BookFile` as configurable options
- **Baseline engine** (`BoardOld.java` / `UciOld.java`) — a frozen snapshot of an
  earlier engine version for head-to-head benchmarking

---

## Project layout

```
CLC12Capstone/
├── book.bin                     Polyglot opening book
├── build.bat                    Compiles all Java sources into Chess Engine/bin/
├── NewEngine.bat                Launches the current engine over UCI
├── OldEngine.bat                Launches the baseline engine over UCI
├── README.md                    This file
├── README_SYZYGY.md             Tablebase feature — design, implementation, usage
└── Chess Engine/
    └── src/
        ├── Board.java           Core engine — board, search, evaluation
        ├── BoardOld.java        Frozen baseline for A/B comparison
        ├── GUI.java             Swing graphical interface
        ├── Main.java            Console interface
        ├── Uci.java             UCI protocol interface
        ├── UciOld.java          UCI wrapper for the baseline engine
        ├── PolyglotBook.java    Opening book reader
        ├── SyzygyTablebase.java Tablebase Java facade
        ├── SyzygyTest.java      Known-answer tablebase validation harness
        ├── syzygy_jni.c         JNI bridge to Fathom (original)
        ├── syzygy.dll           Compiled native library (Windows x64)
        ├── tbprobe.c/h          Fathom probing library (third-party, MIT)
        ├── tbchess.c            Fathom chess logic (third-party, MIT)
        ├── tbconfig.h           Fathom configuration (third-party, MIT)
        ├── stdendian.h          Fathom endianness header (third-party, MIT)
        ├── run.bat              Portable per-class launcher (GUI / Main / Uci)
        ├── syzygy/              Tablebase data files — NOT included, see below
        └── *.png                Piece images
```

---

## Requirements

| Requirement | Details |
|-------------|---------|
| Java | JDK 21 or later |
| OS | Windows (for `syzygy.dll`); source is portable to other platforms |
| Syzygy DLL | Pre-built `syzygy.dll` included for Windows x64; rebuild from source for other platforms (see README_SYZYGY.md) |
| Tablebase files | Optional; not included — see below |

---

## Build

Run `build.bat` once from the repo root before first use, and again any time you
change the source code. It compiles all `.java` files from `Chess Engine/src/` into
`Chess Engine/bin/`:

```
build.bat
```

You should see: `Build succeeded. Compiled classes are in: ...\Chess Engine\bin`

---

## Run

All launchers are relative-path portable — they work regardless of where the project
folder is located.

**Graphical interface:**
```
run.bat GUI
```

**Console / interactive:**
```
run.bat Main
```

**UCI (for connecting to Cute Chess, Arena, etc.):**
```
run.bat Uci
```
Or point your chess GUI directly at `NewEngine.bat` as the engine executable.

**Baseline engine (UCI, for benchmarking):**
```
OldEngine.bat
```

---

## Endgame tablebases (optional)

The engine supports Syzygy endgame tablebases for perfect play in positions with
five or fewer pieces. The tablebase data files are **not included** in this
repository (they are large and not ours to redistribute). To use them:

1. Download a 3–5 piece Syzygy set from **https://syzygy-tables.info**
2. Place the `.rtbw` and `.rtbz` files in `Chess Engine/src/syzygy/`
3. The launchers (`run.bat`, `NewEngine.bat`) configure the path automatically

To verify the integration after downloading:
```
run.bat SyzygyTest
```
Expected output: `ALL SYZYGY TESTS PASSED`

See [README_SYZYGY.md](README_SYZYGY.md) for a full explanation of the feature,
its implementation, and how to rebuild the native library from source.

---

## Third-party components

| Component | Source | Licence | Role |
|-----------|--------|---------|------|
| Fathom (`tbprobe.c`, `tbchess.c`, et al.) | github.com/jdart1/Fathom | MIT | Syzygy tablebase probing |
| Syzygy tablebase format | Ronald de Man | — | Endgame database format |
| Polyglot opening book format | — | — | Opening book standard |

All original Fathom licence headers are preserved in their respective files.

---

## Acknowledgements

Developed as a CLC12 capstone project. The engine architecture, search, evaluation,
move generation, Zobrist hashing, opening book integration, JNI bridge, and all test
harnesses are original work. The Fathom library is included as a dependency for
Syzygy probing and is credited above.
