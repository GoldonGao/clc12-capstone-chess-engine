# Syzygy Endgame Tablebases

## What this feature is

Chess engines play by searching ahead — they try every possible move, evaluate the resulting positions, and pick the best line. This works well in the middlegame, but endgames with few pieces present a challenge: a position that is a forced win might require 30 or more precise moves to convert, far deeper than the engine can search in real time. Without help, the engine can misplay these positions, missing wins or failing to hold draws.

**Endgame tablebases** solve this by pre-computing the correct result for every possible position with a small number of pieces. Not an estimate — the exact, perfect answer for every configuration, calculated offline and stored in files. When the game reaches one of these positions, the engine looks up the answer in under a millisecond instead of searching.

This project integrates the **Syzygy** tablebase format, the modern standard used by professional engines including Stockfish. A five-piece Syzygy set covers every possible position with five or fewer pieces — over one billion positions — all stored in roughly 1 GB of files.

---

## What the files contain

The Syzygy format uses two types of file per material combination (e.g. king + queen vs. king, king + rook vs. king + pawn, etc.):

**`.rtbw` files** (Win/Draw/Loss) — store one of five verdicts for every position: win, cursed win (a win that is too slow to convert before the 50-move draw rule kicks in), draw, blessed loss (a loss that is saved by the 50-move rule), or loss. These are consulted *during search* to immediately cut off any position the tablebases cover, replacing an entire subtree of search with a single file lookup.

**`.rtbz` files** (Distance to Zeroing) — store how many moves remain until the next capture or pawn move under perfect play. These are consulted *when choosing the engine's actual move* to ensure it converts a winning position as efficiently as possible while respecting the 50-move limit.

---

## How it is implemented

Rather than implementing the complex Syzygy file format from scratch (a significant undertaking involving combinatorial mathematics and compressed binary formats), this project uses **Fathom** — an open-source C library that handles the low-level probing. Fathom is the same library used as a reference implementation by many professional engines, and its correctness is well established.

The integration has three layers:

**Fathom** (`tbprobe.c`, `tbprobe.h`, `tbchess.c`) — the external library that opens the tablebase files, computes the mathematical index for a given position, and returns the result. Written in C by "basil" and released under the MIT licence. This code is not original to this project; it is included here as a dependency.

**The JNI bridge** (`syzygy_jni.c`, compiled into `syzygy.dll`) — Java cannot call C code directly, so a small 97-line wrapper translates Java method calls into Fathom function calls. This file *is* original to this project. JNI (Java Native Interface) is Java's standard mechanism for calling native code.

**`SyzygyTablebase.java`** — the Java-side interface. It loads the native library, exposes clean `probeWdl()` and `probeRoot()` methods to the rest of the engine, and handles graceful degradation: if the library or tablebase files are not present, `isReady()` returns false and the engine runs exactly as it always has. The feature is entirely optional.

---

## How the engine uses it

There are two integration points in `Board.java`:

**During search** — at every node in the search tree where the position has five or fewer pieces and no castling rights (castling is not represented in the tablebases), the engine calls `probeWdl()`. If the result is a win, draw, or loss, that value is returned immediately and the entire subtree below is discarded. This means the engine never wastes time searching endgame positions that are already solved. The win/draw/loss scores are carefully chosen to sit below the engine's checkmate scores, so a real forced checkmate is still preferred over a tablebase win, and the transposition table treats the two correctly.

**At the root** — before beginning the search for the engine's move, if the current position is already tablebase-covered, the engine calls `probeRoot()` instead of searching at all. Fathom returns the single best move: the one that preserves the win (or holds the draw) while minimising the distance to a capture or pawn move, keeping the engine safely within the 50-move rule. This is what produced results like "play `a1a4`, you will win in 21 moves" during testing.

A safety guard prevents the engine from probing illegal positions (where the side not to move is in check). Tablebases contain only legal positions, and probing an illegal one would cause the native code to crash. This guard ensures a hand-typed or unusual FEN loaded through the GUI can never crash the engine.

---

## Files in this project

| File | What it is |
|------|------------|
| `SyzygyTablebase.java` | Java interface to the tablebase. Loads the native library, exposes probe methods, handles graceful fallback when tablebases are absent. Original work. |
| `syzygy_jni.c` | C bridge between Java and Fathom. Translates Java method calls into Fathom function calls. Original work. |
| `syzygy.dll` | Compiled native library (Windows x64). Built from `syzygy_jni.c` and Fathom's `tbprobe.c`. |
| `SyzygyTest.java` | Validation harness. Runs known-answer tests against real tablebase files to confirm the full pipeline is correct. Original work. |
| `tbprobe.c`, `tbprobe.h`, `tbchess.c`, `tbconfig.h`, `stdendian.h` | The Fathom probing library. **Third-party code**, © 2015 "basil", MIT licence. Source: https://github.com/jdart1/Fathom |

The tablebase data files (`.rtbw` / `.rtbz`) are **not included** in this repository. They are large (a complete five-piece set is approximately 1 GB) and are freely downloadable from https://syzygy-tables.info.

---

## Third-party attribution

The Fathom library (`tbprobe.c`, `tbprobe.h`, `tbchess.c`, `tbconfig.h`, `stdendian.h`) is the work of "basil" and other contributors, released under the MIT licence. It is included here as a dependency to handle the Syzygy binary format, which involves substantial implementation complexity (compressed file formats, combinatorial position indexing, platform-specific memory mapping) that is beyond the scope of this project to replicate. The original licence headers are preserved in each file.

The Syzygy tablebase format itself was designed by Ronald de Man.
