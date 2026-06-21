
public final class SyzygyTablebase {

    // WDL outcomes, from the side-to-move's perspective. These match Fathom's
    // TB_LOSS .. TB_WIN ordinal constants exactly and must not be reordered.
    public static final int WDL_LOSS = 0;
    public static final int WDL_BLESSED_LOSS = 1;   // loss but drawn by the 50-move rule
    public static final int WDL_DRAW = 2;
    public static final int WDL_CURSED_WIN = 3;      // win but drawn by the 50-move rule
    public static final int WDL_WIN = 4;

    /** Sentinel for "no tablebase result" (probe failed / position not covered). */
    public static final int FAILED = -1;

    private static final boolean NATIVE_AVAILABLE;
    private static volatile boolean ready = false;
    private static volatile int largest = 0;        // max pieces the loaded files cover

    static {
        boolean loaded;
        try {
            System.loadLibrary("syzygy");
            loaded = true;
        } catch (Throwable t) {
            // No native library on java.library.path. Tablebases simply stay off.
            loaded = false;
        }
        NATIVE_AVAILABLE = loaded;
    }

    private SyzygyTablebase() {
    }

    // ---- Native bridges (implemented in syzygy_jni.c against Fathom) ----
    private static native int tbInitNative(String path);          // returns TB_LARGEST, or -1
    private static native void tbFreeNative();
    private static native int tbProbeWdlNative(long white, long black, long kings, long queens,
                                               long rooks, long bishops, long knights, long pawns,
                                               int enPassantSquare, boolean whiteToMove);
    private static native int[] tbProbeRootNative(long white, long black, long kings, long queens,
                                                  long rooks, long bishops, long knights, long pawns,
                                                  int rule50, int enPassantSquare, boolean whiteToMove);

    /**
     * Loads the tablebase files in {@code path}. Returns true if at least 3-man
     * tables became available. Safe to call when the native library is missing
     * (returns false). Idempotent enough to call again with a different path.
     */
    public static synchronized boolean init(String path) {
        if (!NATIVE_AVAILABLE || path == null || path.isBlank()) {
            ready = false;
            return false;
        }
        try {
            int n = tbInitNative(path);
            largest = Math.max(n, 0);
            ready = largest >= 3;
        } catch (Throwable t) {
            largest = 0;
            ready = false;
        }
        return ready;
    }

    /** Releases native resources. Safe to call unconditionally. */
    public static synchronized void free() {
        if (NATIVE_AVAILABLE && ready) {
            try {
                tbFreeNative();
            } catch (Throwable ignored) {
                // best effort
            }
        }
        ready = false;
        largest = 0;
    }

    public static boolean isNativeAvailable() {
        return NATIVE_AVAILABLE;
    }

    public static boolean isReady() {
        return ready;
    }

    /** Largest number of pieces the loaded tables cover (0 if none). */
    public static int maxPieces() {
        return largest;
    }

    /**
     * Probes Win/Draw/Loss for the given position (side-to-move perspective).
     * Returns one of the WDL_* constants, or {@link #FAILED} if the position is
     * not covered or probing failed. The position must have no castling rights and
     * at most {@link #maxPieces()} pieces (the caller is expected to check first).
     */
    public static int probeWdl(long white, long black, long kings, long queens,
                               long rooks, long bishops, long knights, long pawns,
                               int enPassantSquare, boolean whiteToMove) {
        if (!ready) {
            return FAILED;
        }
        try {
            return tbProbeWdlNative(white, black, kings, queens, rooks, bishops, knights, pawns,
                    enPassantSquare, whiteToMove);
        } catch (Throwable t) {
            return FAILED;
        }
    }

    /**
     * Probes the DTZ-optimal root move. Returns {@code int[]{wdl, dtz, from, to, promo}}
     * (from/to as 0..63 squares, promo as 0/1=Q/2=R/3=B/4=N), or {@code null} if the
     * position is not covered, is itself checkmate/stalemate, or probing failed.
     * {@code rule50} is the current halfmove clock so the 50-move rule is honoured.
     */
    public static int[] probeRoot(long white, long black, long kings, long queens,
                                  long rooks, long bishops, long knights, long pawns,
                                  int rule50, int enPassantSquare, boolean whiteToMove) {
        if (!ready) {
            return null;
        }
        try {
            int[] r = tbProbeRootNative(white, black, kings, queens, rooks, bishops, knights, pawns,
                    rule50, enPassantSquare, whiteToMove);
            return (r == null || r.length < 5 || r[0] == FAILED) ? null : r;
        } catch (Throwable t) {
            return null;
        }
    }
}
