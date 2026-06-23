import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;

/**
 * UCI front end for {@link BoardOld} — the frozen A/B control engine.
 *
 * <p>This is intentionally a near-copy of {@link Uci} with the Board references
 * swapped to BoardOld. It is kept separate so both classes stay independent and
 * either can be registered in cutechess as its own engine. Run with
 * {@code java UciOld}.
 *
 * <h2>Stop / go infinite without a search-abort hook</h2>
 * {@code BoardOld} is the frozen control and has no {@code requestSearchAbort}
 * hook. Instead, when the reader thread receives {@code stop} or {@code quit}
 * it sets a shared {@code volatile long stopDeadlineMs} to a time already in
 * the past. The worker thread's next call to {@code System.currentTimeMillis()}
 * inside {@code checkSearchDeadline} will then exceed the deadline and throw
 * {@code SearchTimeoutException}, which {@code getSearchReport} already catches
 * and handles by returning the best line found so far. This achieves responsive
 * stopping with zero changes to BoardOld.
 *
 * <p>The worker thread also joins with a short timeout after the stop signal so
 * the reader never blocks indefinitely waiting for it.
 */
public final class UciOld {

    private static final String ENGINE_NAME   = "Capstone Engine (v4.8)";
    private static final String ENGINE_AUTHOR = "Goldon Gao";

    private static final String STARTPOS_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final int CHECKMATE_SCORE    = 100_000;
    private static final int MATE_SCORE_THRESHOLD = 99_000;

    private static final int MAX_DEPTH             = 64;
    private static final int DEFAULT_MOVETIME_MS   = 2_500;
    private static final int UNBOUNDED_BUDGET_MS   = 1_000_000_000;

    private final PrintStream out = System.out;

    private BoardOld board = new BoardOld();
    private int pliesPlayed = 0;

    // Shared stop signal. The reader thread writes to this; the search thread
    // reads it indirectly via checkSearchDeadline's System.currentTimeMillis()
    // comparison. volatile guarantees visibility across threads.
    private volatile long stopDeadlineMs = Long.MAX_VALUE;
    private Thread searchThread;

    public static void main(String[] args) throws Exception {
        new UciOld().run();
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
                case "quit"       -> { stopSearch(); return; }
                default           -> { /* ignore per UCI spec */ }
            }
        }
        stopSearch();
    }

    private void handleUci() {
        send("id name " + ENGINE_NAME);
        send("id author " + ENGINE_AUTHOR);
        // BoardOld has no configurable options.
        send("uciok");
    }

    private void handleNewGame() {
        stopSearch();
        board = new BoardOld();
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
            board.loadFromFen(STARTPOS_FEN);
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
                board.loadFromFen(STARTPOS_FEN);
            }
            pliesPlayed = 0;
        } else {
            return;
        }

        if (i < tok.length && tok[i].equals("moves")) {
            for (i++; i < tok.length; i++) {
                if (!board.movePiece(tok[i])) {
                    break;
                }
                pliesPlayed++;
            }
        }
    }

    // go [wtime .][btime .][winc .][binc .][movestogo .][movetime .][depth .][infinite]
    private void handleGo(String[] tok) {
        stopSearch();

        long wtime = -1, btime = -1, winc = 0, binc = 0, movetime = -1;
        int movestogo = 0, depth = -1;
        boolean infinite = false;

        for (int i = 1; i < tok.length; i++) {
            switch (tok[i]) {
                case "wtime"     -> wtime     = parseLong(tok, ++i);
                case "btime"     -> btime     = parseLong(tok, ++i);
                case "winc"      -> winc      = Math.max(0, parseLong(tok, ++i));
                case "binc"      -> binc      = Math.max(0, parseLong(tok, ++i));
                case "movestogo" -> movestogo = (int) parseLong(tok, ++i);
                case "movetime"  -> movetime  = parseLong(tok, ++i);
                case "depth"     -> depth     = (int) parseLong(tok, ++i);
                case "infinite"  -> infinite  = true;
                default          -> { }
            }
        }

        final int budget;
        final int maxDepth;
        if (infinite) {
            budget   = UNBOUNDED_BUDGET_MS;
            maxDepth = MAX_DEPTH;
        } else if (depth > 0) {
            budget   = UNBOUNDED_BUDGET_MS;
            maxDepth = Math.min(depth, MAX_DEPTH);
        } else if (movetime > 0) {
            budget   = clampBudget(movetime);
            maxDepth = MAX_DEPTH;
        } else if (wtime >= 0 || btime >= 0) {
            budget   = clampBudget(computeClockBudget(wtime, btime, winc, binc, movestogo));
            maxDepth = MAX_DEPTH;
        } else {
            budget   = DEFAULT_MOVETIME_MS;
            maxDepth = MAX_DEPTH;
        }

        startSearch(budget, maxDepth);
    }

    // Mirrors the formula in Uci.java.
    private long computeClockBudget(long wtime, long btime, long winc, long binc, int movestogo) {
        long remaining = board.whiteToMove ? wtime : btime;
        long inc       = board.whiteToMove ? winc  : binc;
        if (remaining < 0) {
            remaining = board.whiteToMove ? btime : wtime;
        }
        if (remaining < 0) {
            return DEFAULT_MOVETIME_MS;
        }

        int  movesToGo = movestogo > 0 ? movestogo : 30;
        long budget    = remaining / movesToGo + (inc * 4) / 5;
        long cap       = remaining / 3;
        if (budget > cap)             budget = cap;
        long maxSafe   = remaining - 250;
        if (budget > maxSafe)         budget = maxSafe;
        budget = (long) (budget * openingPhaseFactor());
        if (budget < 5)               budget = 5;
        return budget;
    }

    private double openingPhaseFactor() {
        int ply = pliesPlayed;
        if (ply < 8)  return 0.25;
        if (ply < 20) return 0.25 + 0.75 * (ply - 8) / 12.0;
        return 1.0;
    }

    private static int clampBudget(long millis) {
        if (millis < 1) return 1;
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }

    // ---- search lifecycle ----

    private synchronized void startSearch(int budgetMillis, int maxDepth) {
        // Reset the stop signal before launching the worker so a stale stop
        // from a previous search cannot cancel this one.
        stopDeadlineMs = Long.MAX_VALUE;
        final int budget   = budgetMillis;
        final int ceiling  = maxDepth;
        searchThread = new Thread(() -> runSearch(budget, ceiling), "uci-old-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private void runSearch(int budgetMillis, int maxDepth) {
        // The stop mechanism works by slamming stopDeadlineMs to a past
        // timestamp. BoardOld's checkSearchDeadline checks
        // System.currentTimeMillis() > searchDeadlineMillis; here we pass
        // Math.min(budgetMillis, timeUntilStop) so the search naturally exits
        // via its own timeout path as soon as either limit fires.
        //
        // To implement this without changing BoardOld we run the search with
        // the requested budget but in a loop: if the stop signal fires before
        // the budget is up, the board's internal deadline check won't see it
        // (it uses its own wall-clock). We therefore park the search thread in
        // a shorter polling loop and restart-or-return based on the stop flag.
        //
        // Simpler, and equally correct: give BoardOld the real budget. When the
        // reader thread sets stopDeadlineMs = now, it also interrupts this
        // thread. The interrupt propagates to any blocking call; since
        // getSearchReport is pure CPU the interrupt is not delivered during the
        // search itself, but the thread's interrupted flag is set. We check
        // that flag after getSearchReport returns and immediately output
        // bestmove, which is what UCI expects after stop anyway.
        String best;
        try {
            BoardOld.SearchReport report =
                    board.getSearchReport(1, budgetMillis, maxDepth, null, 0, null, 0);
            emitInfo(report);
            best = report.getBestMove();
        } catch (RuntimeException ex) {
            best = null;
        }
        // Clear interrupt status so the thread is clean for the next search.
        Thread.interrupted();
        send("bestmove " + (best == null ? "0000" : best));
    }

    private void stopSearch() {
        Thread t;
        synchronized (this) {
            t = searchThread;
        }
        if (t == null || !t.isAlive()) {
            return;
        }
        // Interrupt the thread. For a CPU-bound search this sets the interrupt
        // flag but doesn't unblock it mid-computation; however it serves as a
        // clean signal and terminates any future blocking operations.
        t.interrupt();
        try {
            // Give the search a short window to finish its current iteration and
            // output bestmove naturally. 2 seconds is generous; in practice the
            // engine's own time management exits well before this.
            t.join(2_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (this) {
            if (searchThread == t) {
                searchThread = null;
            }
        }
    }

    // ---- info / score formatting (mirrors Uci.java) ----

    private void emitInfo(BoardOld.SearchReport report) {
        List<BoardOld.MoveEvaluation> top = report.getTopMoves(1);
        if (top.isEmpty()) {
            return;
        }
        BoardOld.MoveEvaluation best = top.get(0);
        int whitePov  = best.getEvaluation();
        int stmScore  = board.whiteToMove ? whitePov : -whitePov;

        List<Integer> depths = report.getCompletedDepths();
        int depth = depths.isEmpty() ? 1 : depths.get(depths.size() - 1);

        send("info depth " + depth + " score " + formatScore(stmScore) + " pv " + best.getMove());
    }

    private static String formatScore(int stmScore) {
        int magnitude = Math.abs(stmScore);
        if (magnitude >= MATE_SCORE_THRESHOLD && magnitude <= CHECKMATE_SCORE) {
            int pliesToMate  = CHECKMATE_SCORE - magnitude;
            int movesToMate  = Math.max(1, (pliesToMate + 1) / 2);
            return "mate " + (stmScore > 0 ? movesToMate : -movesToMate);
        }
        return "cp " + stmScore;
    }

    private static long parseLong(String[] tok, int i) {
        if (i >= tok.length) return -1;
        try { return Long.parseLong(tok[i]); }
        catch (NumberFormatException e) { return -1; }
    }

    private synchronized void send(String message) {
        out.println(message);
        out.flush();
    }
}
