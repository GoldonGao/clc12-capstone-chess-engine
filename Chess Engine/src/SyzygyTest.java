import java.util.List;

/**
 * Validation harness for Syzygy tablebase probing. Run this on the machine that
 * has the tablebase files and the built native library:
 *
 *   java -Djava.library.path=DIR_WITH_NATIVE_LIB SyzygyTest "PATH_TO_SYZYGY_DIR"
 *
 * e.g.
 *   java -Djava.library.path=. SyzygyTest "C:\Users\golde\CLC12Capstone\Chess Engine\src\syzygy"
 *
 * If the native library or files are not present it reports that and skips
 * (it does not fail the build). When they are present it checks unambiguous
 * verdicts (KQ/KR vs K are wins, KR vs KR is a draw), color-mirror consistency,
 * and that the DTZ root probe returns a legal converting move.
 */
public class SyzygyTest {
    static int fails = 0;

    public static void main(String[] args) {
        String path = args.length > 0 ? args[0] : System.getProperty("syzygyPath", "");
        if (!SyzygyTablebase.isNativeAvailable()) {
            System.out.println("SKIP: native library 'syzygy' not on java.library.path. "
                    + "Build it (see README_SYZYGY.md) and pass -Djava.library.path=<dir>.");
            return;
        }
        if (path.isBlank() || !SyzygyTablebase.init(path)) {
            System.out.println("SKIP: tablebases not loaded from '" + path + "'. "
                    + "Pass the directory holding the .rtbw/.rtbz files as the first argument.");
            return;
        }
        System.out.println("Loaded tablebases up to " + SyzygyTablebase.maxPieces() + " pieces from " + path);

        // Unambiguous WDL verdicts (side to move, with the material safe).
        // NOTE: tablebases contain only LEGAL positions. A position where the side
        // NOT to move is in check is illegal and must never be probed.
        checkWdl("KQ vs K is a win",  "8/8/8/8/8/4k3/8/3QK3 w - - 0 1", SyzygyTablebase.WDL_WIN);
        checkWdl("KR vs K is a win",  "8/8/8/8/8/3k4/8/R3K3 w - - 0 1", SyzygyTablebase.WDL_WIN);
        checkWdl("KR vs KR is a draw","3rk3/8/8/8/8/8/8/R3K3 w - - 0 1", SyzygyTablebase.WDL_DRAW);

        // Color-mirror consistency: a win for the side to move must read as a loss
        // for the other side in the mirrored position.
        checkMirror("KQ vs K mirror", "8/8/8/8/8/4k3/8/3QK3 w - - 0 1");
        checkMirror("KR vs K mirror", "8/8/8/8/8/3k4/8/R3K3 w - - 0 1");

        // DTZ root probe returns a legal move that holds the win.
        checkRootMove("KQ vs K root move", "8/8/8/8/8/4k3/8/3QK3 w - - 0 1");
        checkRootMove("KR vs K root move", "8/8/8/8/8/3k4/8/R3K3 w - - 0 1");

        System.out.println(fails == 0 ? "\nALL SYZYGY TESTS PASSED" : "\n" + fails + " FAILURES");
        if (fails != 0) {
            System.exit(1);
        }
    }

    static int wdlOf(String fen) {
        Board b = new Board();
        b.loadFromFen(fen);
        long white = b.getWhitePawns() | b.getWhiteKnights() | b.getWhiteBishops()
                | b.getWhiteRooks() | b.getWhiteQueen() | b.getWhiteKing();
        long black = b.getBlackPawns() | b.getBlackKnights() | b.getBlackBishops()
                | b.getBlackRooks() | b.getBlackQueen() | b.getBlackKing();
        return SyzygyTablebase.probeWdl(white, black,
                b.getWhiteKing() | b.getBlackKing(), b.getWhiteQueen() | b.getBlackQueen(),
                b.getWhiteRooks() | b.getBlackRooks(), b.getWhiteBishops() | b.getBlackBishops(),
                b.getWhiteKnights() | b.getBlackKnights(), b.getWhitePawns() | b.getBlackPawns(),
                0, fen.contains(" w "));
    }

    static void checkWdl(String label, String fen, int expected) {
        int got = wdlOf(fen);
        report(label, expected, got);
    }

    static void checkMirror(String label, String fen) {
        int direct = wdlOf(fen);
        int mirrored = wdlOf(mirror(fen));
        // The mirror swaps piece colors, flips the board vertically, AND flips the
        // side to move -- i.e. it relabels the position from the other player's point
        // of view. The side-to-move-relative WDL is therefore IDENTICAL, not opposite.
        boolean ok = direct != SyzygyTablebase.FAILED && direct == mirrored;
        if (ok) {
            System.out.println("  PASS  " + label + "  (wdl=" + direct + " both sides)");
        } else {
            fails++;
            System.out.println("  FAIL  " + label + "  direct=" + direct + " mirrored=" + mirrored
                    + " (should be equal)");
        }
    }

    static void checkRootMove(String label, String fen) {
        Board b = new Board();
        b.loadFromFen(fen);
        long white = b.getWhitePawns() | b.getWhiteKnights() | b.getWhiteBishops()
                | b.getWhiteRooks() | b.getWhiteQueen() | b.getWhiteKing();
        long black = b.getBlackPawns() | b.getBlackKnights() | b.getBlackBishops()
                | b.getBlackRooks() | b.getBlackQueen() | b.getBlackKing();
        int[] r = SyzygyTablebase.probeRoot(white, black,
                b.getWhiteKing() | b.getBlackKing(), b.getWhiteQueen() | b.getBlackQueen(),
                b.getWhiteRooks() | b.getBlackRooks(), b.getWhiteBishops() | b.getBlackBishops(),
                b.getWhiteKnights() | b.getBlackKnights(), b.getWhitePawns() | b.getBlackPawns(),
                0, 0, fen.contains(" w "));
        if (r == null) {
            fails++;
            System.out.println("  FAIL  " + label + "  root probe returned null");
            return;
        }
        // Convert from/to (0..63) into the engine's algebraic form and check legality.
        String move = sq(r[2]) + sq(r[3]) + (r[4] == 1 ? "q" : r[4] == 2 ? "r" : r[4] == 3 ? "b" : r[4] == 4 ? "n" : "");
        List<String> legal = b.getLegalMoves();
        boolean ok = legal.contains(move) && r[0] == SyzygyTablebase.WDL_WIN;
        if (ok) {
            System.out.println("  PASS  " + label + "  move=" + move + " wdl=" + r[0] + " dtz=" + r[1]);
        } else {
            fails++;
            System.out.println("  FAIL  " + label + "  move=" + move + " legal=" + legal.contains(move)
                    + " wdl=" + r[0]);
        }
    }

    static void report(String label, int expected, int got) {
        if (expected == got) {
            System.out.println("  PASS  " + label + "  (wdl=" + got + ")");
        } else {
            fails++;
            System.out.println("  FAIL  " + label + "  expected wdl=" + expected + " got=" + got);
        }
    }

    static String sq(int index) {
        return "" + (char) ('a' + (index % 8)) + (char) ('1' + (index / 8));
    }

    // Vertical color mirror of a FEN (ranks reversed, case swapped, side flipped).
    static String mirror(String fen) {
        String[] parts = fen.split(" ");
        String[] ranks = parts[0].split("/");
        StringBuilder sb = new StringBuilder();
        for (int i = ranks.length - 1; i >= 0; i--) {
            for (char ch : ranks[i].toCharArray()) {
                sb.append(Character.isUpperCase(ch) ? Character.toLowerCase(ch)
                        : Character.isLowerCase(ch) ? Character.toUpperCase(ch) : ch);
            }
            if (i > 0) sb.append('/');
        }
        return sb + " " + (parts[1].equals("w") ? "b" : "w") + " - - 0 1";
    }
}
