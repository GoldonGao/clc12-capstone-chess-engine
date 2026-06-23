import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

public final class Uci {


    private static final String ENGINE_NAME = "Capstone Engine v5.0";
    private static final String ENGINE_AUTHOR = "Goldon Gao";

    private static final String STARTPOS_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    // Mirrors the engine's own score constants (Board keeps these private).
    private static final int CHECKMATE_SCORE = 100_000;
    private static final int MATE_SCORE_THRESHOLD = 99_000;

    private static final int MAX_DEPTH = 64;            // matches Board.MAX_SEARCH_DEPTH
    private static final int DEFAULT_MOVETIME_MS = 2_500; // used when "go" gives no limits
    // A budget large enough that the engine's predictive time control never
    // curtails a fixed-depth or infinite search; "stop" ends those instead.
    private static final int UNBOUNDED_BUDGET_MS = 1_000_000_000;

    private final PrintStream out = System.out;
    private Board board = new Board();
    private PolyglotBook book = PolyglotBook.openDefault();   // null if no book configured/found
    private int pliesPlayed = 0;        // moves applied since the current game's base position
    private Thread searchThread;        // the in-flight search, if any (guarded by 'this')

    public static void main(String[] args) throws Exception {
        // Optional: pre-load Syzygy tablebases from -DsyzygyPath=... so a launcher
        // script can configure everything. A GUI can still override later via
        // "setoption name SyzygyPath value ...". No-op if the path is unset or the
        // native library/files are missing.
        String syzygyPath = System.getProperty("syzygyPath", "");
        if (!syzygyPath.isBlank()) {
            SyzygyTablebase.init(syzygyPath);
        }
        new Uci().run();
    }

    private void run() throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] tok = line.split("\\s+");
            switch (tok[0]) {
                case "uci"        -> handleUci();
                case "isready"    -> send("readyok");
                case "ucinewgame" -> handleNewGame();
                case "position"   -> handlePosition(tok);
                case "go"         -> handleGo(tok);
                case "stop"       -> stopSearch();
                case "setoption"  -> handleSetOption(tok);
                case "quit"       -> { stopSearch(); return; }
                // Recognised but intentionally unsupported -> ignored:
                // ponderhit/ponder, debug, register, and any unknown token
                // (UCI requires ignoring these).
                default           -> { /* ignore */ }
            }
        }
        stopSearch();
    }

    private void handleUci() {
        send("id name " + ENGINE_NAME);
        send("id author " + ENGINE_AUTHOR);
        // The opening book is the only configurable feature exposed over UCI.
        send("option name OwnBook type check default " + (book != null && book.isEnabled()));
        send("option name BookFile type string default <none>");
        send("option name SyzygyPath type string default <none>");
        send("uciok");
    }

    // setoption name <Name> [value <Value...>]. Only OwnBook and BookFile are
    // meaningful; everything else is accepted and ignored.
    private void handleSetOption(String[] tok) {
        int i = 1;
        if (i < tok.length && tok[i].equalsIgnoreCase("name")) {
            i++;
        }
        StringBuilder name = new StringBuilder();
        for (; i < tok.length && !tok[i].equalsIgnoreCase("value"); i++) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(tok[i]);
        }
        StringBuilder value = new StringBuilder();
        if (i < tok.length && tok[i].equalsIgnoreCase("value")) {
            for (i++; i < tok.length; i++) {
                if (value.length() > 0) {
                    value.append(' ');
                }
                value.append(tok[i]);
            }
        }
        String optionName = name.toString();
        String optionValue = value.toString().trim();

        if (optionName.equalsIgnoreCase("OwnBook")) {
            boolean on = optionValue.equalsIgnoreCase("true");
            if (on && book == null) {
                book = PolyglotBook.openDefault();
            }
            if (book != null) {
                book.setEnabled(on);
            }
        } else if (optionName.equalsIgnoreCase("BookFile")) {
            PolyglotBook loaded = PolyglotBook.openOrNull(optionValue);
            if (loaded != null) {
                book = loaded;
            }
        } else if (optionName.equalsIgnoreCase("SyzygyPath")) {
            // Load Syzygy endgame tablebases from the given directory. A no-op if the
            // native probing library is not present; the engine then runs as usual.
            if (!optionValue.isEmpty() && !optionValue.equals("<none>")) {
                boolean ok = SyzygyTablebase.init(optionValue);
                send("info string SyzygyPath " + (ok
                        ? "loaded tablebases up to " + SyzygyTablebase.maxPieces() + " pieces"
                        : "no tablebases loaded (native library or files missing)"));
            }
        }
    }

    private void handleNewGame() {
        stopSearch();
        board = new Board();   // fresh instance => fresh transposition table for the new game
        pliesPlayed = 0;
    }

    // position [startpos | fen <FEN>] [moves <m1> <m2> ...]
    private void handlePosition(String[] tok) {
        stopSearch();
        int i = 1;
        if (i >= tok.length) {
            return;
        }
        if (tok[i].equals("startpos")) {
            board.loadFromFen(STARTPOS_FEN);   // reuse the instance to keep the TT across the game
            pliesPlayed = 0;
            i++;
        } else if (tok[i].equals("fen")) {
            StringBuilder fen = new StringBuilder();
            i++;
            while (i < tok.length && !tok[i].equals("moves")) {
                if (fen.length() > 0) {
                    fen.append(' ');
                }
                fen.append(tok[i]);
                i++;
            }
            if (!board.loadFromFen(fen.toString())) {
                board.loadFromFen(STARTPOS_FEN);   // defensive fallback on a malformed FEN
            }
            pliesPlayed = 0;
        } else {
            return;
        }

        if (i < tok.length && tok[i].equals("moves")) {
            for (i++; i < tok.length; i++) {
                if (!board.movePiece(tok[i])) {
                    break;   // illegal/garbled token: stop applying further moves
                }
                pliesPlayed++;
            }
        }
    }

    // go [wtime .][btime .][winc .][binc .][movestogo .][movetime .][depth .][infinite] ...
    private void handleGo(String[] tok) {
        stopSearch();

        long wtime = -1, btime = -1, winc = 0, binc = 0, movetime = -1;
        int movestogo = 0, depth = -1;
        boolean infinite = false;

        for (int i = 1; i < tok.length; i++) {
            switch (tok[i]) {
                case "wtime"     -> wtime = parseLong(tok, ++i);
                case "btime"     -> btime = parseLong(tok, ++i);
                case "winc"      -> winc = Math.max(0, parseLong(tok, ++i));
                case "binc"      -> binc = Math.max(0, parseLong(tok, ++i));
                case "movestogo" -> movestogo = (int) parseLong(tok, ++i);
                case "movetime"  -> movetime = parseLong(tok, ++i);
                case "depth"     -> depth = (int) parseLong(tok, ++i);
                case "infinite"  -> infinite = true;
                // nodes, mate, searchmoves, ponder: not supported -> their values
                // (if any) are skipped by the loop naturally / ignored.
                default          -> { /* ignore */ }
            }
        }

        // Opening book: for normal play (not analysis), an in-book move is
        // returned instantly, which also banks clock time. "go infinite" is an
        // analysis request, so the book is bypassed there.
        if (!infinite && book != null) {
            String bookMove = book.probe(board);
            if (bookMove != null) {
                send("info string book move " + bookMove);
                send("bestmove " + bookMove);
                return;
            }
        }

        final int budget;
        final int maxDepth;
        if (infinite) {
            budget = UNBOUNDED_BUDGET_MS;          // ended by "stop"
            maxDepth = MAX_DEPTH;
        } else if (depth > 0) {
            budget = UNBOUNDED_BUDGET_MS;          // search to the requested depth
            maxDepth = Math.min(depth, MAX_DEPTH);
        } else if (movetime > 0) {
            budget = clampBudget(movetime);
            maxDepth = MAX_DEPTH;
        } else if (wtime >= 0 || btime >= 0) {
            budget = clampBudget(computeClockBudget(wtime, btime, winc, binc, movestogo));
            maxDepth = MAX_DEPTH;
        } else {
            budget = DEFAULT_MOVETIME_MS;          // unconstrained "go" -> fixed default
            maxDepth = MAX_DEPTH;
        }

        startSearch(budget, maxDepth);
    }

    // Derive a per-move budget from the side-to-move's own clock, mirroring the
    // GUI's scheme: split the remaining time over an assumed number of moves,
    // add most of the increment, cap so we never spend too large a fraction or
    // overrun the clock, and play the opening quickly to bank time.
    private long computeClockBudget(long wtime, long btime, long winc, long binc, int movestogo) {
        long remaining = board.whiteToMove ? wtime : btime;
        long inc = board.whiteToMove ? winc : binc;
        if (remaining < 0) {
            remaining = board.whiteToMove ? btime : wtime;   // fall back to the only clock given
        }
        if (remaining < 0) {
            return DEFAULT_MOVETIME_MS;
        }

        int movesToGo = movestogo > 0 ? movestogo : 30;
        long budget = remaining / movesToGo + (inc * 4) / 5;

        long cap = remaining / 3;
        if (budget > cap) {
            budget = cap;
        }
        long maxSafe = remaining - 250;   // leave a safety margin so we never flag
        if (budget > maxSafe) {
            budget = maxSafe;
        }

        budget = (long) (budget * openingPhaseFactor());
        if (budget < 5) {
            budget = 5;
        }
        return budget;
    }

    private double openingPhaseFactor() {
        int ply = pliesPlayed;
        if (ply < 8) {
            return 0.25;
        }
        if (ply < 20) {
            return 0.25 + 0.75 * (ply - 8) / 12.0;
        }
        return 1.0;
    }

    private static int clampBudget(long millis) {
        if (millis < 1) {
            return 1;
        }
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }

    // ---- search lifecycle ----

    private synchronized void startSearch(int budgetMillis, int maxDepth) {
        // Clear any prior abort on this (the reader) thread BEFORE the worker
        // starts, so a later stop on this same thread always wins the ordering.
        board.clearSearchAbort();
        final int budget = budgetMillis;
        final int ceiling = maxDepth;
        searchThread = new Thread(() -> runSearch(budget, ceiling), "uci-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void runSearch(int budgetMillis, int maxDepth) {
        String best;
        try {
            Board.SearchReport report =
                    board.getSearchReport(1, budgetMillis, maxDepth, null, 0, null, 0);
            emitInfo(report);
            best = report.getBestMove();
        } catch (RuntimeException ex) {
            best = null;   // should not happen for a legal position; answer safely
        }
        // UCI requires the null/none move token when there is no move to make.
        send("bestmove " + (best == null ? "0000" : best));
    }

    private void stopSearch() {
        Thread t;
        synchronized (this) {
            t = searchThread;
        }
        if (t == null) {
            return;
        }
        board.requestSearchAbort();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            if (searchThread == t) {
                searchThread = null;
            }
        }
    }

    // ---- info / score formatting ----

    private void emitInfo(Board.SearchReport report) {
        List<Board.MoveEvaluation> top = report.getTopMoves(1);
        if (top.isEmpty()) {
            return;
        }
        Board.MoveEvaluation best = top.get(0);
        int whitePov = best.getEvaluation();
        int stmScore = board.whiteToMove ? whitePov : -whitePov;   // to side-to-move POV

        List<Integer> depths = report.getCompletedDepths();
        int depth = depths.isEmpty() ? 1 : depths.get(depths.size() - 1);

        send("info depth " + depth + " score " + formatScore(stmScore) + " pv " + best.getMove());
    }

    private static String formatScore(int stmScore) {
        int magnitude = Math.abs(stmScore);
        if (magnitude >= MATE_SCORE_THRESHOLD && magnitude <= CHECKMATE_SCORE) {
            int pliesToMate = CHECKMATE_SCORE - magnitude;
            int movesToMate = (pliesToMate + 1) / 2;
            if (movesToMate == 0) {
                movesToMate = 1;   // guard; a root move always leaves >= 1 ply to mate
            }
            return "mate " + (stmScore > 0 ? movesToMate : -movesToMate);
        }
        return "cp " + stmScore;
    }

    private static long parseLong(String[] tok, int i) {
        if (i >= tok.length) {
            return -1;
        }
        try {
            return Long.parseLong(tok[i]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private synchronized void send(String message) {
        out.println(message);
        out.flush();
    }
}
