import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Board {
    private static final int PAWN_VALUE = 100;
    private static final int KNIGHT_VALUE = 320;
    private static final int BISHOP_VALUE = 330;
    private static final int ROOK_VALUE = 500;
    private static final int QUEEN_VALUE = 900;
    private static final int KING_VALUE = 20000;
    private static final int CHECKMATE_SCORE = 100000;
    private static final int SEARCH_INFINITY = 1000000;
    private static final int DEFAULT_MOVE_TIME_LIMIT_MILLIS = 5000;
    private static final int DEFAULT_MAX_SEARCH_DEPTH = 5;
    private static final int CENTER_CONTROL_BONUS = 10;
    private static final int CENTER_PAWN_OCCUPATION_BONUS = 20;
    private static final int OPENING_MOVE_LIMIT = 10;
    private static final int UNDEVELOPED_MINOR_PENALTY = 30;
    private static final int OPENING_CENTER_PAWN_BONUS = 25;
    private static final int OPENING_CENTER_PAWN_CONTROL_BONUS = 15;
    // Aspiration windows: after the first iteration, search the next depth with a
    // narrow window around the previous score, widening on a fail. Active only
    // when no move-variety adjustments are in play (see getMoveEvaluations), so
    // the only root adjustment is the +/-1 temperature, which this width absorbs.
    private static final int ASPIRATION_WINDOW = 50;
    private int lastRawBestScore;
    private static final int TRANSPOSITION_EXACT = 0;
    private static final int TRANSPOSITION_ALPHA = 1;
    private static final int TRANSPOSITION_BETA = 2;
    private static final int[] CENTER_SQUARES = {27, 28, 35, 36};
    private static final int[] WHITE_MINOR_START_SQUARES = {1, 2, 5, 6};
    private static final int[] BLACK_MINOR_START_SQUARES = {57, 58, 61, 62};
    private static final String NO_DISCOURAGED_SOURCE_SQUARE = null;
    private static final List<String> NO_DEVELOPED_SOURCE_SQUARES = null;

    // ---- Tapered (PeSTO) evaluation tables ----
    // Source: Rofchade-derived PeSTO piece/square tables. Material value and the
    // positional table are folded together into a midgame (MG) and an endgame (EG)
    // table per piece type, and the final score interpolates between them by the
    // remaining-material "game phase". The raw tables below are written in a8=0
    // orientation (rank 8 first); they are flipped to this engine's a1=0 indexing
    // when the combined MG_TABLE/EG_TABLE are built, so the existing white->square,
    // black->vertically-mirrored-square convention applies unchanged.
    private static final int[] PESTO_MG_VALUE = {82, 337, 365, 477, 1025, 0};
    private static final int[] PESTO_EG_VALUE = {94, 281, 297, 512, 936, 0};
    // Phase weight per piece type (pawn, knight, bishop, rook, queen, king).
    private static final int[] PESTO_PHASE_INC = {0, 1, 1, 2, 4, 0};
    private static final int PESTO_PHASE_MAX = 24;

    private static final int[] PESTO_MG_PAWN = {
          0,   0,   0,   0,   0,   0,  0,   0,
         98, 134,  61,  95,  68, 126, 34, -11,
         -6,   7,  26,  31,  65,  56, 25, -20,
        -14,  13,   6,  21,  23,  12, 17, -23,
        -27,  -2,  -5,  12,  17,   6, 10, -25,
        -26,  -4,  -4, -10,   3,   3, 33, -12,
        -35,  -1, -20, -23, -15,  24, 38, -22,
          0,   0,   0,   0,   0,   0,  0,   0,
    };
    private static final int[] PESTO_EG_PAWN = {
          0,   0,   0,   0,   0,   0,   0,   0,
        178, 173, 158, 134, 147, 132, 165, 187,
         94, 100,  85,  67,  56,  53,  82,  84,
         32,  24,  13,   5,  -2,   4,  17,  17,
         13,   9,  -3,  -7,  -7,  -8,   3,  -1,
          4,   7,  -6,   1,   0,  -5,  -1,  -8,
         13,   8,   8,  10,  13,   0,   2,  -7,
          0,   0,   0,   0,   0,   0,   0,   0,
    };
    private static final int[] PESTO_MG_KNIGHT = {
        -167, -89, -34, -49,  61, -97, -15, -107,
         -73, -41,  72,  36,  23,  62,   7,  -17,
         -47,  60,  37,  65,  84, 129,  73,   44,
          -9,  17,  19,  53,  37,  69,  18,   22,
         -13,   4,  16,  13,  28,  19,  21,   -8,
         -23,  -9,  12,  10,  19,  17,  25,  -16,
         -29, -53, -12,  -3,  -1,  18, -14,  -19,
        -105, -21, -58, -33, -17, -28, -19,  -23,
    };
    private static final int[] PESTO_EG_KNIGHT = {
        -58, -38, -13, -28, -31, -27, -63, -99,
        -25,  -8, -25,  -2,  -9, -25, -24, -52,
        -24, -20,  10,   9,  -1,  -9, -19, -41,
        -17,   3,  22,  22,  22,  11,   8, -18,
        -18,  -6,  16,  25,  16,  17,   4, -18,
        -23,  -3,  -1,  15,  10,  -3, -20, -22,
        -42, -20, -10,  -5,  -2, -20, -23, -44,
        -29, -51, -23, -15, -22, -18, -50, -64,
    };
    private static final int[] PESTO_MG_BISHOP = {
        -29,   4, -82, -37, -25, -42,   7,  -8,
        -26,  16, -18, -13,  30,  59,  18, -47,
        -16,  37,  43,  40,  35,  50,  37,  -2,
         -4,   5,  19,  50,  37,  37,   7,  -2,
         -6,  13,  13,  26,  34,  12,  10,   4,
          0,  15,  15,  15,  14,  27,  18,  10,
          4,  15,  16,   0,   7,  21,  33,   1,
        -33,  -3, -14, -21, -13, -12, -39, -21,
    };
    private static final int[] PESTO_EG_BISHOP = {
        -14, -21, -11,  -8, -7,  -9, -17, -24,
         -8,  -4,   7, -12, -3, -13,  -4, -14,
          2,  -8,   0,  -1, -2,   6,   0,   4,
         -3,   9,  12,   9, 14,  10,   3,   2,
         -6,   3,  13,  19,  7,  10,  -3,  -9,
        -12,  -3,   8,  10, 13,   3,  -7, -15,
        -14, -18,  -7,  -1,  4,  -9, -15, -27,
        -23,  -9, -23,  -5, -9, -16,  -5, -17,
    };
    private static final int[] PESTO_MG_ROOK = {
         32,  42,  32,  51, 63,  9,  31,  43,
         27,  32,  58,  62, 80, 67,  26,  44,
         -5,  19,  26,  36, 17, 45,  61,  16,
        -24, -11,   7,  26, 24, 35,  -8, -20,
        -36, -26, -12,  -1,  9, -7,   6, -23,
        -45, -25, -16, -17,  3,  0,  -5, -33,
        -44, -16, -20,  -9, -1, 11,  -6, -71,
        -19, -13,   1,  17, 16,  7, -37, -26,
    };
    private static final int[] PESTO_EG_ROOK = {
        13, 10, 18, 15, 12,  12,   8,   5,
        11, 13, 13, 11, -3,   3,   8,   3,
         7,  7,  7,  5,  4,  -3,  -5,  -3,
         4,  3, 13,  1,  2,   1,  -1,   2,
         3,  5,  8,  4, -5,  -6,  -8, -11,
        -4,  0, -5, -1, -7, -12,  -8, -16,
        -6, -6,  0,  2, -9,  -9, -11,  -3,
        -9,  2,  3, -1, -5, -13,   4, -20,
    };
    private static final int[] PESTO_MG_QUEEN = {
        -28,   0,  29,  12,  59,  44,  43,  45,
        -24, -39,  -5,   1, -16,  57,  28,  54,
        -13, -17,   7,   8,  29,  56,  47,  57,
        -27, -27, -16, -16,  -1,  17,  -2,   1,
         -9, -26,  -9, -10,  -2,  -4,   3,  -3,
        -14,   2, -11,  -2,  -5,   2,  14,   5,
        -35,  -8,  11,   2,   8,  15,  -3,   1,
         -1, -18,  -9,  10, -15, -25, -31, -50,
    };
    private static final int[] PESTO_EG_QUEEN = {
         -9,  22,  22,  27,  27,  19,  10,  20,
        -17,  20,  32,  41,  58,  25,  30,   0,
        -20,   6,   9,  49,  47,  35,  19,   9,
          3,  22,  24,  45,  57,  40,  57,  36,
        -18,  28,  19,  47,  31,  34,  39,  23,
        -16, -27,  15,   6,   9,  17,  10,   5,
        -22, -23, -30, -16, -16, -23, -36, -32,
        -33, -28, -22, -43,  -5, -32, -20, -41,
    };
    private static final int[] PESTO_MG_KING = {
        -65,  23,  16, -15, -56, -34,   2,  13,
         29,  -1, -20,  -7,  -8,  -4, -38, -29,
         -9,  24,   2, -16, -20,   6,  22, -22,
        -17, -20, -12, -27, -30, -25, -14, -36,
        -49,  -1, -27, -39, -46, -44, -33, -51,
        -14, -14, -22, -46, -44, -30, -15, -27,
          1,   7,  -8, -64, -43, -16,   9,   8,
        -15,  36,  12, -54,   8, -28,  24,  14,
    };
    private static final int[] PESTO_EG_KING = {
        -74, -35, -18, -18, -11,  15,   4, -17,
        -12,  17,  14,  17,  17,  38,  23,  11,
         10,  17,  23,  15,  20,  45,  44,  13,
         -8,  22,  24,  27,  26,  33,  26,   3,
        -18,  -4,  21,  24,  27,  23,   9, -11,
        -19,  -3,  11,  21,  23,  16,   7,  -9,
        -27, -11,   4,  13,  14,   4,  -5, -17,
        -53, -34, -21, -11, -28, -14, -24, -43,
    };

    private static final int[][] PESTO_MG_RAW = {
            PESTO_MG_PAWN, PESTO_MG_KNIGHT, PESTO_MG_BISHOP, PESTO_MG_ROOK, PESTO_MG_QUEEN, PESTO_MG_KING};
    private static final int[][] PESTO_EG_RAW = {
            PESTO_EG_PAWN, PESTO_EG_KNIGHT, PESTO_EG_BISHOP, PESTO_EG_ROOK, PESTO_EG_QUEEN, PESTO_EG_KING};

    // Combined material+PST tables in a1=0 orientation: MG_TABLE[type][square] is
    // the midgame value of a white piece of that type standing on that square.
    private static final int[][] MG_TABLE = new int[6][64];
    private static final int[][] EG_TABLE = new int[6][64];
    static {
        for (int type = 0; type < 6; type++) {
            for (int square = 0; square < 64; square++) {
                int pestoSquare = square ^ 56;   // a1=0 -> a8=0 (rank flip)
                MG_TABLE[type][square] = PESTO_MG_VALUE[type] + PESTO_MG_RAW[type][pestoSquare];
                EG_TABLE[type][square] = PESTO_EG_VALUE[type] + PESTO_EG_RAW[type][pestoSquare];
            }
        }
    }

    public boolean whiteToMove;
    private String castlingRights;
    private String enPassantTarget;
    private int halfmoveClock;
    private int fullmoveNumber;
    private long whitePawns;
    private long whiteKnights;
    private long whiteBishops;
    private long whiteRooks;
    private long whiteQueen;
    private long whiteKing;
    private long blackPawns;
    private long blackKnights;
    private long blackBishops;
    private long blackRooks;
    private long blackQueen;
    private long blackKing;
    private Map<String, Integer> positionHistory;
    private static final int TRANSPOSITION_TABLE_SIZE = 1 << 20;
    private static final int TRANSPOSITION_TABLE_MASK = TRANSPOSITION_TABLE_SIZE - 1;
    private static final int MATE_SCORE_THRESHOLD = CHECKMATE_SCORE - 1000;
    // Tablebase win/loss score: clearly decisive (far above any heuristic eval) yet
    // below MATE_SCORE_THRESHOLD so a real forced mate is still preferred and the TT
    // never mistakes a tablebase score for a mate score. NO_TB_VALUE marks "no result".
    private static final int TB_WIN_SCORE = 90000;
    private static final int NO_TB_VALUE = Integer.MIN_VALUE;
    private int tablebaseHits;
    // Iterative deepening is driven by the clock up to this hard safety ceiling.
    private static final int MAX_SEARCH_DEPTH = 64;
    // A new iteration of iterative deepening tends to cost about as much as all
    // previous iterations combined, so once this percentage of the budget has been
    // spent, starting another iteration would almost certainly be cut off and
    // discarded; better to stop and return the deepest completed result.
    private static final int ITERATION_START_THRESHOLD_PERCENT = 50;
    private TranspositionEntry[] transpositionTable;
    private int searchGeneration;
    // Cooperative stop signal for an in-progress search. Set from another thread
    // (e.g. a UCI "stop"/"quit" handler) to make the current search abort at the
    // next deadline check and return the best line found so far. It is cleared at
    // the start of every search, never affects perft or evaluation, and is the
    // only addition needed to support UCI stop/infinite analysis.
    private volatile boolean searchAbortRequested;
    private final int[][] historyHeuristic = new int[64][64];
    private final int[] killerMoves1 = new int[64];
    private final int[] killerMoves2 = new int[64];

    // ---- Evaluation tuning constants ----
    private static final int DOUBLED_PAWN_PENALTY = 15;
    private static final int ISOLATED_PAWN_PENALTY = 12;
    private static final int BACKWARD_PAWN_PENALTY = 8;
    private static final int[] PASSED_PAWN_BONUS = {0, 10, 17, 28, 45, 70, 110, 0};
    private static final int BISHOP_PAIR_BONUS = 30;
    private static final int ROOK_OPEN_FILE_BONUS = 20;
    private static final int ROOK_SEMI_OPEN_FILE_BONUS = 10;
    private static final int ROOK_ON_SEVENTH_BONUS = 20;
    private static final int MOBILITY_WEIGHT = 2;
    // ---- King safety ----
    private static final int KS_SHIELD_MISSING_PENALTY = 12;
    private static final int KS_SHIELD_ADVANCED_PENALTY = 6;
    private static final int KS_OPEN_FILE_PENALTY = 12;
    private static final int KS_HALF_OPEN_FILE_PENALTY = 6;
    private static final int KS_PAWN_ATTACK_WEIGHT = 1;
    private static final int KS_KNIGHT_ATTACK_WEIGHT = 2;
    private static final int KS_BISHOP_ATTACK_WEIGHT = 2;
    private static final int KS_ROOK_ATTACK_WEIGHT = 3;
    private static final int KS_QUEEN_ATTACK_WEIGHT = 4;
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] QUEEN_DIRECTIONS = {
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final long[] FILE_MASK = new long[8];
    private static final long[] KNIGHT_ATTACK_MASK = new long[64];
    private static final long[] KING_ATTACK_MASK = new long[64];

    // ---- Zobrist hashing tables (fixed seed for reproducibility) ----
    private static final long[][] ZOBRIST_PIECE = new long[12][64];
    private static final long[] ZOBRIST_CASTLING = new long[16];
    private static final long[] ZOBRIST_EP_FILE = new long[8];
    private static final long ZOBRIST_SIDE;
    // Incrementally maintained Zobrist key for the current position. It is kept
    // in lock-step with the board by makeMove/unmakeMove so the search can read
    // it directly instead of rescanning every piece at every node. It always
    // equals computeZobristKey() for the current position (asserted exhaustively
    // by verifyZobristPerft); computeZobristKey remains the source of truth used
    // to (re)initialise it whenever the position is set up or copied.
    private long positionZobristKey;
    // Debug flag (default off): when true, the search asserts at every TT-probe
    // node that the incrementally maintained key matches a from-scratch
    // recomputation. Kept as a cheap regression guard — a future search feature
    // that makes a move without maintaining the key would silently corrupt the
    // TT (perft cannot catch it), and flipping this on in a test would.
    static boolean VERIFY_ZOBRIST_IN_SEARCH = false;

    static {
        for (int file = 0; file < 8; file++) {
            long mask = 0L;
            for (int rank = 0; rank < 8; rank++) {
                mask |= 1L << (rank * 8 + file);
            }
            FILE_MASK[file] = mask;
        }

        int[][] knightOffsets = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
        int[][] kingOffsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int square = 0; square < 64; square++) {
            int rank = square / 8;
            int file = square % 8;
            long knightMask = 0L;
            for (int[] offset : knightOffsets) {
                int r = rank + offset[0];
                int f = file + offset[1];
                if (r >= 0 && r < 8 && f >= 0 && f < 8) {
                    knightMask |= 1L << (r * 8 + f);
                }
            }
            KNIGHT_ATTACK_MASK[square] = knightMask;

            long kingMask = 0L;
            for (int[] offset : kingOffsets) {
                int r = rank + offset[0];
                int f = file + offset[1];
                if (r >= 0 && r < 8 && f >= 0 && f < 8) {
                    kingMask |= 1L << (r * 8 + f);
                }
            }
            KING_ATTACK_MASK[square] = kingMask;
        }

        java.util.Random random = new java.util.Random(0x9E3779B97F4A7C15L);
        for (int piece = 0; piece < 12; piece++) {
            for (int square = 0; square < 64; square++) {
                ZOBRIST_PIECE[piece][square] = random.nextLong();
            }
        }
        for (int i = 0; i < ZOBRIST_CASTLING.length; i++) {
            ZOBRIST_CASTLING[i] = random.nextLong();
        }
        for (int i = 0; i < ZOBRIST_EP_FILE.length; i++) {
            ZOBRIST_EP_FILE[i] = random.nextLong();
        }
        ZOBRIST_SIDE = random.nextLong();
    }

    public Board() {
        loadStartingPosition();
    }

    public boolean loadFromFen(String fen) {
        if (fen == null) {
            return false;
        }

        String trimmedFen = fen.trim();
        if (trimmedFen.isEmpty()) {
            return false;
        }

        String[] fields = trimmedFen.split("\\s+");
        if (fields.length == 0) {
            return false;
        }

        long newWhitePawns = 0L;
        long newWhiteKnights = 0L;
        long newWhiteBishops = 0L;
        long newWhiteRooks = 0L;
        long newWhiteQueen = 0L;
        long newWhiteKing = 0L;
        long newBlackPawns = 0L;
        long newBlackKnights = 0L;
        long newBlackBishops = 0L;
        long newBlackRooks = 0L;
        long newBlackQueen = 0L;
        long newBlackKing = 0L;

        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) {
            return false;
        }

        for (int fenRank = 0; fenRank < 8; fenRank++) {
            int file = 0;
            String rankData = ranks[fenRank];

            for (int i = 0; i < rankData.length(); i++) {
                char symbol = rankData.charAt(i);

                if (Character.isDigit(symbol)) {
                    file += symbol - '0';
                    continue;
                }

                if (file >= 8 || !isValidFenPiece(symbol)) {
                    return false;
                }

                int boardRank = 7 - fenRank;
                int squareIndex = boardRank * 8 + file;
                long squareMask = 1L << squareIndex;

                switch (symbol) {
                    case 'P' -> newWhitePawns |= squareMask;
                    case 'N' -> newWhiteKnights |= squareMask;
                    case 'B' -> newWhiteBishops |= squareMask;
                    case 'R' -> newWhiteRooks |= squareMask;
                    case 'Q' -> newWhiteQueen |= squareMask;
                    case 'K' -> newWhiteKing |= squareMask;
                    case 'p' -> newBlackPawns |= squareMask;
                    case 'n' -> newBlackKnights |= squareMask;
                    case 'b' -> newBlackBishops |= squareMask;
                    case 'r' -> newBlackRooks |= squareMask;
                    case 'q' -> newBlackQueen |= squareMask;
                    case 'k' -> newBlackKing |= squareMask;
                    default -> {
                        return false;
                    }
                }

                file++;
            }

            if (file != 8) {
                return false;
            }
        }

        if (fields.length > 1 && !fields[1].equals("w") && !fields[1].equals("b")) {
            return false;
        }

        if (fields.length > 2 && !isValidCastlingRights(fields[2])) {
            return false;
        }

        if (fields.length > 3 && !isValidEnPassantSquare(fields[3])) {
            return false;
        }

        if (fields.length > 4 && !isNonNegativeInteger(fields[4])) {
            return false;
        }

        if (fields.length > 5 && !isPositiveInteger(fields[5])) {
            return false;
        }

        whitePawns = newWhitePawns;
        whiteKnights = newWhiteKnights;
        whiteBishops = newWhiteBishops;
        whiteRooks = newWhiteRooks;
        whiteQueen = newWhiteQueen;
        whiteKing = newWhiteKing;
        blackPawns = newBlackPawns;
        blackKnights = newBlackKnights;
        blackBishops = newBlackBishops;
        blackRooks = newBlackRooks;
        blackQueen = newBlackQueen;
        blackKing = newBlackKing;
        whiteToMove = fields.length < 2 || fields[1].equals("w");
        castlingRights = fields.length > 2 ? fields[2] : "KQkq";
        enPassantTarget = fields.length > 3 ? fields[3] : "-";
        halfmoveClock = fields.length > 4 ? Integer.parseInt(fields[4]) : 0;
        fullmoveNumber = fields.length > 5 ? Integer.parseInt(fields[5]) : 1;
        resetPositionHistory();
        positionZobristKey = computeZobristKey();

        return true;
    }

    private void loadStartingPosition() {
        whiteToMove = true;

        // starting position using a1 = bit 0, h8 = bit 63.

        //White's pieces
        whitePawns = 0x000000000000FF00L;
        whiteKnights = 0x0000000000000042L;
        whiteBishops = 0x0000000000000024L;
        whiteRooks = 0x0000000000000081L;
        whiteQueen = 0x0000000000000008L;
        whiteKing = 0x0000000000000010L;

        //Black's pieces
        blackPawns = 0x00FF000000000000L;
        blackKnights = 0x4200000000000000L;
        blackBishops = 0x2400000000000000L;
        blackRooks = 0x8100000000000000L;
        blackQueen = 0x0800000000000000L;
        blackKing = 0x1000000000000000L;
        castlingRights = "KQkq";
        enPassantTarget = "-";
        halfmoveClock = 0;
        fullmoveNumber = 1;
        resetPositionHistory();
        positionZobristKey = computeZobristKey();
    }

    public String toFen() {
        StringBuilder fen = new StringBuilder();

        for (int rank = 7; rank >= 0; rank--) {
            int emptySquares = 0;

            for (int file = 0; file < 8; file++) {
                char piece = getPieceAt(rank * 8 + file);
                if (piece == '.') {
                    emptySquares++;
                    continue;
                }

                if (emptySquares > 0) {
                    fen.append(emptySquares);
                    emptySquares = 0;
                }
                fen.append(piece);
            }

            if (emptySquares > 0) {
                fen.append(emptySquares);
            }

            if (rank > 0) {
                fen.append('/');
            }
        }

        fen.append(' ')
           .append(whiteToMove ? 'w' : 'b')
           .append(' ')
           .append(castlingRights)
           .append(' ')
           .append(enPassantTarget)
           .append(' ')
           .append(halfmoveClock)
           .append(' ')
           .append(fullmoveNumber);

        return fen.toString();
    }

    public long getWhitePawns() {
        return whitePawns;
    }
    public long getWhiteKnights() {
        return whiteKnights;
    }
    public long getWhiteBishops() {
        return whiteBishops;
    }
    public long getWhiteRooks() {
        return whiteRooks;
    }
    public long getWhiteQueen() {
        return whiteQueen;
    }
    public long getWhiteKing() {
        return whiteKing;
    }
    public long getBlackPawns() {
        return blackPawns;
    }
    public long getBlackKnights() {
        return blackKnights;
    }
    public long getBlackBishops() {
        return blackBishops;
    }
    public long getBlackRooks() {
        return blackRooks;
    }
    public long getBlackQueen() {
        return blackQueen;
    }
    public long getBlackKing() {
        return blackKing;
    }

    // Position-keyed evaluation cache. The evaluation is a pure function of the
    // board, so it is keyed by the Zobrist key and stays valid across moves and
    // searches. Memoising it avoids recomputing the (now-relatively-expensive)
    // evaluation on transposed and revisited positions, especially in quiescence.
    private static final int EVAL_CACHE_SIZE = 1 << 20;       // entries (power of two)
    private static final int EVAL_CACHE_MASK = EVAL_CACHE_SIZE - 1;
    private long[] evalCacheKeys;
    private int[] evalCacheValues;

    // Quiescence transposition table. Quiescence values are a pure function of the
    // position (material-based, no mate scores since checkmate returns stand-pat),
    // so entries stay valid across moves/searches. Bound flags mirror the main TT.
    private static final int QTT_SIZE = 1 << 20;
    private static final int QTT_MASK = QTT_SIZE - 1;
    private long[] qttKeys;
    private int[] qttValues;
    private byte[] qttFlags;

    private void storeQuiescence(long key, int value, int origAlpha, int origBeta, int ply) {
        int flag = TRANSPOSITION_EXACT;
        if (value <= origAlpha) {
            flag = TRANSPOSITION_ALPHA;
        } else if (value >= origBeta) {
            flag = TRANSPOSITION_BETA;
        }
        int idx = (int) (key & QTT_MASK);
        qttKeys[idx] = key;
        qttValues[idx] = adjustMateScoreForStorage(value, ply);
        qttFlags[idx] = (byte) flag;
    }

    private int cachedEvaluation() {
        if (evalCacheKeys == null) {
            evalCacheKeys = new long[EVAL_CACHE_SIZE];
            evalCacheValues = new int[EVAL_CACHE_SIZE];
        }
        long key = positionZobristKey;
        if (VERIFY_ZOBRIST_IN_SEARCH && key != computeZobristKey()) {
            throw new IllegalStateException("eval-cache key stale at " + toFen());
        }
        int idx = (int) (key & EVAL_CACHE_MASK);
        if (evalCacheKeys[idx] == key && key != 0L) {
            int cached = evalCacheValues[idx];
            if (VERIFY_ZOBRIST_IN_SEARCH && cached != getMaterialEvaluationCentipawns()) {
                throw new IllegalStateException("eval-cache wrong hit at " + toFen());
            }
            return cached;
        }
        int value = getMaterialEvaluationCentipawns();
        evalCacheKeys[idx] = key;
        evalCacheValues[idx] = value;
        return value;
    }

    // White-positive tapered material+PST score, interpolating between the midgame
    // and endgame tables by remaining-material game phase. Replaces the previous
    // single (non-tapered) material+PST term.
    private int getTaperedPstEvaluation() {
        int whiteMg = 0, whiteEg = 0, blackMg = 0, blackEg = 0, phase = 0;
        long[] whiteBoards = {whitePawns, whiteKnights, whiteBishops, whiteRooks, whiteQueen, whiteKing};
        long[] blackBoards = {blackPawns, blackKnights, blackBishops, blackRooks, blackQueen, blackKing};
        for (int type = 0; type < 6; type++) {
            long wb = whiteBoards[type];
            while (wb != 0) {
                int square = Long.numberOfTrailingZeros(wb);
                whiteMg += MG_TABLE[type][square];
                whiteEg += EG_TABLE[type][square];
                phase += PESTO_PHASE_INC[type];
                wb &= wb - 1;
            }
            long bb = blackBoards[type];
            while (bb != 0) {
                int square = Long.numberOfTrailingZeros(bb) ^ 56;   // black: vertical mirror
                blackMg += MG_TABLE[type][square];
                blackEg += EG_TABLE[type][square];
                phase += PESTO_PHASE_INC[type];
                bb &= bb - 1;
            }
        }
        int mgScore = whiteMg - blackMg;
        int egScore = whiteEg - blackEg;
        int mgPhase = Math.min(phase, PESTO_PHASE_MAX);   // guard early promotions
        int egPhase = PESTO_PHASE_MAX - mgPhase;
        return (mgScore * mgPhase + egScore * egPhase) / PESTO_PHASE_MAX;
    }

    public int getMaterialEvaluationCentipawns() {
        return getTaperedPstEvaluation()
                + getCenterControlEvaluation()
                + getCenterPawnOccupationEvaluation()
                + getPawnStructureEvaluation()
                + getMobilityEvaluation()
                + getBishopPairEvaluation()
                + getRookEvaluation()
                + getKingSafetyEvaluation();
    }

    private int getCenterControlEvaluation() {
        int evaluation = 0;

        for (int squareIndex : CENTER_SQUARES) {
            if (isSquareAttacked(squareIndex, true)) {
                evaluation += CENTER_CONTROL_BONUS;
            }
            if (isSquareAttacked(squareIndex, false)) {
                evaluation -= CENTER_CONTROL_BONUS;
            }
        }

        return evaluation;
    }

    private int getCenterPawnOccupationEvaluation() {
        int evaluation = 0;

        for (int squareIndex : CENTER_SQUARES) {
            long squareMask = 1L << squareIndex;
            if ((whitePawns & squareMask) != 0) {
                evaluation += CENTER_PAWN_OCCUPATION_BONUS;
            }
            if ((blackPawns & squareMask) != 0) {
                evaluation -= CENTER_PAWN_OCCUPATION_BONUS;
            }
        }

        return evaluation;
    }

    // ---- Pawn structure: doubled, isolated, passed and backward pawns ----
    private int getPawnStructureEvaluation() {
        return evaluatePawnsForColor(true) - evaluatePawnsForColor(false);
    }

    private int evaluatePawnsForColor(boolean white) {
        long ownPawns = white ? whitePawns : blackPawns;
        long enemyPawns = white ? blackPawns : whitePawns;
        int score = 0;

        for (int file = 0; file < 8; file++) {
            int count = Long.bitCount(ownPawns & FILE_MASK[file]);
            if (count > 1) {
                score -= (count - 1) * DOUBLED_PAWN_PENALTY;
            }
        }

        long remaining = ownPawns;
        while (remaining != 0) {
            int square = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1;
            int file = square % 8;
            int rank = square / 8;

            long adjacentFiles = (file > 0 ? FILE_MASK[file - 1] : 0L)
                    | (file < 7 ? FILE_MASK[file + 1] : 0L);

            if ((ownPawns & adjacentFiles) == 0) {
                score -= ISOLATED_PAWN_PENALTY;
            }

            if (isPassedPawn(white, file, rank, enemyPawns)) {
                int relativeRank = white ? rank : 7 - rank;
                score += PASSED_PAWN_BONUS[relativeRank];
            }

            if (isBackwardPawn(white, square, file, rank, ownPawns, adjacentFiles)) {
                score -= BACKWARD_PAWN_PENALTY;
            }
        }

        return score;
    }

    private boolean isPassedPawn(boolean white, int file, int rank, long enemyPawns) {
        long blockingFiles = FILE_MASK[file]
                | (file > 0 ? FILE_MASK[file - 1] : 0L)
                | (file < 7 ? FILE_MASK[file + 1] : 0L);
        long aheadMask = white ? ranksAbove(rank) : ranksBelow(rank);
        return (enemyPawns & blockingFiles & aheadMask) == 0;
    }

    private boolean isBackwardPawn(boolean white, int square, int file, int rank, long ownPawns, long adjacentFiles) {
        // No friendly pawn on an adjacent file that is level or further back, so it
        // cannot be supported by a neighbour advancing alongside it.
        long supportMask = white ? ranksAtOrBelow(rank) : ranksAtOrAbove(rank);
        if ((ownPawns & adjacentFiles & supportMask) != 0) {
            return false;
        }
        int stopSquare = white ? square + 8 : square - 8;
        if (stopSquare < 0 || stopSquare >= 64) {
            return false;
        }
        return isAttackedByPawn(stopSquare, !white);
    }

    private long ranksAbove(int rank) {
        if (rank >= 7) {
            return 0L;
        }
        return ~((1L << ((rank + 1) * 8)) - 1);
    }

    private long ranksBelow(int rank) {
        if (rank <= 0) {
            return 0L;
        }
        return (1L << (rank * 8)) - 1;
    }

    private long ranksAtOrBelow(int rank) {
        if (rank >= 7) {
            return -1L;
        }
        return (1L << ((rank + 1) * 8)) - 1;
    }

    private long ranksAtOrAbove(int rank) {
        if (rank <= 0) {
            return -1L;
        }
        return ~((1L << (rank * 8)) - 1);
    }

    // ---- Mobility (pseudo-legal attack squares not blocked by own pieces) ----
    private int getMobilityEvaluation() {
        return MOBILITY_WEIGHT * (countMobility(true) - countMobility(false));
    }

    private int countMobility(boolean white) {
        long ownPieces = white ? getWhitePieces() : getBlackPieces();
        long occupied = getWhitePieces() | getBlackPieces();
        long notOwn = ~ownPieces;
        int mobility = 0;

        long knights = white ? whiteKnights : blackKnights;
        while (knights != 0) {
            int square = Long.numberOfTrailingZeros(knights);
            knights &= knights - 1;
            mobility += Long.bitCount(KNIGHT_ATTACK_MASK[square] & notOwn);
        }

        long king = white ? whiteKing : blackKing;
        if (king != 0) {
            int square = Long.numberOfTrailingZeros(king);
            mobility += Long.bitCount(KING_ATTACK_MASK[square] & notOwn);
        }

        mobility += slidingMobility(white ? whiteBishops : blackBishops, occupied, notOwn, BISHOP_DIRECTIONS);
        mobility += slidingMobility(white ? whiteRooks : blackRooks, occupied, notOwn, ROOK_DIRECTIONS);
        mobility += slidingMobility(white ? whiteQueen : blackQueen, occupied, notOwn, QUEEN_DIRECTIONS);
        return mobility;
    }

    private int slidingMobility(long pieces, long occupied, long notOwn, int[][] directions) {
        int mobility = 0;
        long remaining = pieces;
        while (remaining != 0) {
            int square = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1;
            mobility += Long.bitCount(slidingAttacks(square, occupied, directions) & notOwn);
        }
        return mobility;
    }

    private long slidingAttacks(int square, long occupied, int[][] directions) {
        long attacks = 0L;
        int rank = square / 8;
        int file = square % 8;
        for (int[] direction : directions) {
            int r = rank + direction[0];
            int f = file + direction[1];
            while (r >= 0 && r < 8 && f >= 0 && f < 8) {
                int target = r * 8 + f;
                long mask = 1L << target;
                attacks |= mask;
                if ((occupied & mask) != 0) {
                    break;
                }
                r += direction[0];
                f += direction[1];
            }
        }
        return attacks;
    }

    private int getBishopPairEvaluation() {
        int evaluation = 0;
        if (Long.bitCount(whiteBishops) >= 2) {
            evaluation += BISHOP_PAIR_BONUS;
        }
        if (Long.bitCount(blackBishops) >= 2) {
            evaluation -= BISHOP_PAIR_BONUS;
        }
        return evaluation;
    }

    // ---- Rooks on open/semi-open files and on the 7th rank ----
    private int getRookEvaluation() {
        return evaluateRooksForColor(true) - evaluateRooksForColor(false);
    }

    private int evaluateRooksForColor(boolean white) {
        long rooks = white ? whiteRooks : blackRooks;
        long ownPawns = white ? whitePawns : blackPawns;
        long enemyPawns = white ? blackPawns : whitePawns;
        int seventhRank = white ? 6 : 1;
        int score = 0;

        long remaining = rooks;
        while (remaining != 0) {
            int square = Long.numberOfTrailingZeros(remaining);
            remaining &= remaining - 1;
            int file = square % 8;
            int rank = square / 8;

            boolean ownPawnOnFile = (ownPawns & FILE_MASK[file]) != 0;
            boolean enemyPawnOnFile = (enemyPawns & FILE_MASK[file]) != 0;
            if (!ownPawnOnFile && !enemyPawnOnFile) {
                score += ROOK_OPEN_FILE_BONUS;
            } else if (!ownPawnOnFile) {
                score += ROOK_SEMI_OPEN_FILE_BONUS;
            }

            if (rank == seventhRank) {
                score += ROOK_ON_SEVENTH_BONUS;
            }
        }

        return score;
    }

    // ---- King safety ----
    // White-positive: a more exposed black king raises the score, a more exposed
    // white king lowers it.
    private int getKingSafetyEvaluation() {
        return computeKingDanger(false) - computeKingDanger(true);
    }

    private int computeKingDanger(boolean white) {
        long kingBitboard = white ? whiteKing : blackKing;
        if (kingBitboard == 0) {
            return 0;
        }
        int kingSquare = Long.numberOfTrailingZeros(kingBitboard);
        int kingFile = kingSquare % 8;
        int kingRank = kingSquare / 8;

        long enemyQueen = white ? blackQueen : whiteQueen;
        long enemyRooks = white ? blackRooks : whiteRooks;
        long enemyMinors = white ? (blackKnights | blackBishops) : (whiteKnights | whiteBishops);
        // How much force the enemy can bring to bear; with nothing to attack with,
        // an exposed king is not actually in danger (and an active king is good).
        int attackPotential = Long.bitCount(enemyQueen) * 4
                + Long.bitCount(enemyRooks) * 2
                + Long.bitCount(enemyMinors);
        if (attackPotential == 0) {
            return 0;
        }

        long ownPawns = white ? whitePawns : blackPawns;
        long enemyPawns = white ? blackPawns : whitePawns;
        int danger = 0;

        int lowFile = Math.max(0, kingFile - 1);
        int highFile = Math.min(7, kingFile + 1);
        for (int file = lowFile; file <= highFile; file++) {
            // Pawn shield: prefer a friendly pawn directly in front of the king.
            int rankAhead = white ? kingRank + 1 : kingRank - 1;
            int rankTwoAhead = white ? kingRank + 2 : kingRank - 2;
            boolean shieldAdjacent = rankAhead >= 0 && rankAhead < 8
                    && (ownPawns & (1L << (rankAhead * 8 + file))) != 0;
            boolean shieldAdvanced = rankTwoAhead >= 0 && rankTwoAhead < 8
                    && (ownPawns & (1L << (rankTwoAhead * 8 + file))) != 0;
            if (!shieldAdjacent && !shieldAdvanced) {
                danger += KS_SHIELD_MISSING_PENALTY;
            } else if (!shieldAdjacent) {
                danger += KS_SHIELD_ADVANCED_PENALTY;
            }

            // Open and half-open files pointing at the king.
            boolean ownPawnOnFile = (ownPawns & FILE_MASK[file]) != 0;
            boolean enemyPawnOnFile = (enemyPawns & FILE_MASK[file]) != 0;
            if (!ownPawnOnFile && !enemyPawnOnFile) {
                danger += KS_OPEN_FILE_PENALTY;
            } else if (!ownPawnOnFile) {
                danger += KS_HALF_OPEN_FILE_PENALTY;
            }
        }

        // Enemy pieces bearing down on the squares around the king.
        long kingZone = KING_ATTACK_MASK[kingSquare] | kingBitboard;
        danger += kingZoneAttackUnits(!white, kingZone);

        // Scale by available enemy attacking force.
        return danger * Math.min(attackPotential, 8) / 8;
    }

    private int kingZoneAttackUnits(boolean attackerWhite, long kingZone) {
        long occupied = getWhitePieces() | getBlackPieces();
        int units = 0;

        long pawns = attackerWhite ? whitePawns : blackPawns;
        while (pawns != 0) {
            int square = Long.numberOfTrailingZeros(pawns);
            pawns &= pawns - 1;
            units += KS_PAWN_ATTACK_WEIGHT * Long.bitCount(pawnAttackMask(square, attackerWhite) & kingZone);
        }

        long knights = attackerWhite ? whiteKnights : blackKnights;
        while (knights != 0) {
            int square = Long.numberOfTrailingZeros(knights);
            knights &= knights - 1;
            units += KS_KNIGHT_ATTACK_WEIGHT * Long.bitCount(KNIGHT_ATTACK_MASK[square] & kingZone);
        }

        long bishops = attackerWhite ? whiteBishops : blackBishops;
        while (bishops != 0) {
            int square = Long.numberOfTrailingZeros(bishops);
            bishops &= bishops - 1;
            units += KS_BISHOP_ATTACK_WEIGHT
                    * Long.bitCount(slidingAttacks(square, occupied, BISHOP_DIRECTIONS) & kingZone);
        }

        long rooks = attackerWhite ? whiteRooks : blackRooks;
        while (rooks != 0) {
            int square = Long.numberOfTrailingZeros(rooks);
            rooks &= rooks - 1;
            units += KS_ROOK_ATTACK_WEIGHT
                    * Long.bitCount(slidingAttacks(square, occupied, ROOK_DIRECTIONS) & kingZone);
        }

        long queens = attackerWhite ? whiteQueen : blackQueen;
        while (queens != 0) {
            int square = Long.numberOfTrailingZeros(queens);
            queens &= queens - 1;
            units += KS_QUEEN_ATTACK_WEIGHT
                    * Long.bitCount(slidingAttacks(square, occupied, QUEEN_DIRECTIONS) & kingZone);
        }

        return units;
    }

    private long pawnAttackMask(int square, boolean white) {
        int file = square % 8;
        long mask = 0L;
        if (white) {
            if (file > 0 && square + 7 < 64) {
                mask |= 1L << (square + 7);
            }
            if (file < 7 && square + 9 < 64) {
                mask |= 1L << (square + 9);
            }
        } else {
            if (file > 0 && square - 9 >= 0) {
                mask |= 1L << (square - 9);
            }
            if (file < 7 && square - 7 >= 0) {
                mask |= 1L << (square - 7);
            }
        }
        return mask;
    }

    public void printBoard() {
        printBoard(whiteToMove);
    }

    public void printBoard(boolean whitePerspective) {
        char[] squares = new char[64];
        for (int i = 0; i < squares.length; i++) {
            squares[i] = '·';
        }

        fillSquares(squares, whitePawns, 'P');
        fillSquares(squares, whiteKnights, 'N');
        fillSquares(squares, whiteBishops, 'B');
        fillSquares(squares, whiteRooks, 'R');
        fillSquares(squares, whiteQueen, 'Q');
        fillSquares(squares, whiteKing, 'K');
        fillSquares(squares, blackPawns, 'p');
        fillSquares(squares, blackKnights, 'n');
        fillSquares(squares, blackBishops, 'b');
        fillSquares(squares, blackRooks, 'r');
        fillSquares(squares, blackQueen, 'q');
        fillSquares(squares, blackKing, 'k');

        String[] files = whitePerspective
                ? new String[]{"a", "b", "c", "d", "e", "f", "g", "h"}
                : new String[]{"h", "g", "f", "e", "d", "c", "b", "a"};

        System.out.println("    " + String.join(" ", files));
        System.out.println("   ________________");
        int startRank = whitePerspective ? 7 : 0;
        int endRank = whitePerspective ? -1 : 8;
        int rankStep = whitePerspective ? -1 : 1;
        int startFile = whitePerspective ? 0 : 7;
        int endFile = whitePerspective ? 8 : -1;
        int fileStep = whitePerspective ? 1 : -1;

        for (int rank = startRank; rank != endRank; rank += rankStep) {
            System.out.print((rank + 1) + " |");
            boolean firstFile = true;
            for (int file = startFile; file != endFile; file += fileStep) {
                if (firstFile) {
                    System.out.print(" ");
                    firstFile = false;
                }
                System.out.print(squares[rank * 8 + file] + " ");
            }
            System.out.println("| " + (rank + 1));
        }
        System.out.println("   ________________");
        System.out.println("    " + String.join(" ", files));
    }

    public boolean movePiece(String move) {
        String resolvedMove = resolveMoveInput(move, true);
        if (resolvedMove == null) {
            return false;
        }
        return tryMove(resolvedMove, true);
    }

    public long countBoardStates(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("Depth must be non-negative");
        }

        if (depth == 0) {
            return 1L;
        }

        long total = 0L;
        for (int move : generateLegalMoves()) {
            UndoInfo undo = makeMove(move);
            total += countBoardStates(depth - 1);
            unmakeMove(undo);
        }

        return total;
    }

    /**
     * Verification harness for incremental Zobrist hashing. Walks the move tree
     * to {@code depth} exactly like {@link #countBoardStates}, but at every node
     * (and after every make and unmake) asserts that the incrementally maintained
     * key equals a from-scratch {@link #computeZobristKey()}. Returns the perft
     * node count, so a caller can simultaneously confirm move generation is
     * unchanged. Throws {@link IllegalStateException} on the first mismatch.
     */
    public long verifyZobristPerft(int depth) {
        if (positionZobristKey != computeZobristKey()) {
            throw new IllegalStateException("Zobrist mismatch (node): incremental="
                    + positionZobristKey + " scratch=" + computeZobristKey() + " fen=" + toFen());
        }
        if (depth == 0) {
            return 1L;
        }
        long total = 0L;
        for (int move : generateLegalMoves()) {
            UndoInfo undo = makeMove(move);
            if (positionZobristKey != computeZobristKey()) {
                throw new IllegalStateException("Zobrist mismatch after make " + moveToString(move)
                        + ": incremental=" + positionZobristKey + " scratch=" + computeZobristKey()
                        + " fen=" + toFen());
            }
            total += verifyZobristPerft(depth - 1);
            unmakeMove(undo);
            if (positionZobristKey != computeZobristKey()) {
                throw new IllegalStateException("Zobrist mismatch after unmake " + moveToString(move)
                        + ": incremental=" + positionZobristKey + " scratch=" + computeZobristKey());
            }
        }
        return total;
    }

    public String getBestMove(int depth) {
        return getBestMove(depth, DEFAULT_MOVE_TIME_LIMIT_MILLIS, DEFAULT_MAX_SEARCH_DEPTH);
    }

    public String getBestMove(int depth, String discouragedSourceSquare, int discouragedMovePenalty) {
        return getBestMove(depth, DEFAULT_MOVE_TIME_LIMIT_MILLIS, DEFAULT_MAX_SEARCH_DEPTH,
                discouragedSourceSquare, discouragedMovePenalty, NO_DEVELOPED_SOURCE_SQUARES, 0);
    }

    public String getBestMove(int depth, String discouragedSourceSquare, int discouragedMovePenalty,
                              List<String> developedSourceSquares, int varietyDevelopmentBonus) {
        return getBestMove(depth, DEFAULT_MOVE_TIME_LIMIT_MILLIS, DEFAULT_MAX_SEARCH_DEPTH,
                discouragedSourceSquare, discouragedMovePenalty, developedSourceSquares, varietyDevelopmentBonus);
    }

    public String getBestMove(int minimumDepth, int timeLimitMillis, int maximumDepth) {
        return getBestMove(minimumDepth, timeLimitMillis, maximumDepth,
                NO_DISCOURAGED_SOURCE_SQUARE, 0, NO_DEVELOPED_SOURCE_SQUARES, 0);
    }

    public String getBestMove(int minimumDepth, int timeLimitMillis, int maximumDepth,
                              String discouragedSourceSquare, int discouragedMovePenalty) {
        return getBestMove(minimumDepth, timeLimitMillis, maximumDepth,
                discouragedSourceSquare, discouragedMovePenalty, NO_DEVELOPED_SOURCE_SQUARES, 0);
    }

    public String getBestMove(int minimumDepth, int timeLimitMillis, int maximumDepth,
                              String discouragedSourceSquare, int discouragedMovePenalty,
                              List<String> developedSourceSquares, int varietyDevelopmentBonus) {
        SearchReport report = getSearchReport(minimumDepth, timeLimitMillis, maximumDepth,
                discouragedSourceSquare, discouragedMovePenalty, developedSourceSquares, varietyDevelopmentBonus);
        return report.getBestMove();
    }

    public SearchReport getSearchReport(int minimumDepth, int timeLimitMillis, int maximumDepth,
                                        String discouragedSourceSquare, int discouragedMovePenalty,
                                        List<String> developedSourceSquares, int varietyDevelopmentBonus) {
        if (minimumDepth < 1) {
            throw new IllegalArgumentException("Depth must be at least 1");
        }
        if (timeLimitMillis < 1) {
            throw new IllegalArgumentException("Time limit must be positive");
        }
        if (maximumDepth < minimumDepth) {
            throw new IllegalArgumentException("Maximum depth must be at least the minimum depth");
        }

        resetSearchHeuristics();

        // Root tablebase probe: in a covered position, DTZ gives the optimal move
        // directly (perfect, 50-move-rule-aware play), so we skip the search. No-op
        // when no tablebases are loaded. Falls through to search if the probe fails.
        if (tablebaseEligible()) {
            SearchReport tbReport = probeTablebaseRootReport();
            if (tbReport != null) {
                return tbReport;
            }
        }

        long searchStartMillis = System.currentTimeMillis();
        long searchDeadlineMillis = searchStartMillis + timeLimitMillis;
        SearchReport report = new SearchReport();
        // The search mutates this board in place via make/unmake. A timeout is
        // raised by throwing, which unwinds the stack and skips the pending
        // unmakeMove calls, so we snapshot the entry state and restore it on
        // exit to guarantee the board is left exactly as it was found.
        Board entryState = copy();
        try {
            // Guaranteed fallback from a cheap static (depth-0) ranking, so a legal
            // move is always available even if no full depth completes in time.
            report.setFallbackMoves(getStaticMoveEvaluations());
            int depthCeiling = Math.min(maximumDepth, MAX_SEARCH_DEPTH);
            for (int depth = 1; depth <= depthCeiling; depth++) {
                // Predictive time control: a new iteration tends to cost about as
                // much as everything before it, so once half the budget is spent,
                // starting another would almost certainly be cut off and discarded.
                // Stopping here lets the clock, not an arbitrary depth cap, decide
                // how deep to search, and avoids throwing away a half-done iteration.
                if (depth > 1) {
                    long elapsed = System.currentTimeMillis() - searchStartMillis;
                    if (elapsed * 100L >= (long) timeLimitMillis * ITERATION_START_THRESHOLD_PERCENT) {
                        break;
                    }
                }
                try {
                    // Every real depth obeys the deadline; the search cannot overrun
                    // the time budget it was given.
                    List<MoveEvaluation> moveEvaluations = getMoveEvaluations(depth, searchDeadlineMillis,
                            discouragedSourceSquare, discouragedMovePenalty, developedSourceSquares,
                            varietyDevelopmentBonus, lastRawBestScore, depth > 1);
                    report.recordCompletedDepth(depth, moveEvaluations);
                } catch (SearchTimeoutException ex) {
                    report.markTimedOut();
                    break;
                }
            }
        } finally {
            copyStateFrom(entryState);
        }

        return report;
    }

    public List<String> getBestMoves(int depth) {
        return getBestMoves(depth, Long.MAX_VALUE, NO_DISCOURAGED_SOURCE_SQUARE, 0,
                NO_DEVELOPED_SOURCE_SQUARES, 0);
    }

    public List<String> getBestMoves(int depth, String discouragedSourceSquare, int discouragedMovePenalty) {
        return getBestMoves(depth, Long.MAX_VALUE, discouragedSourceSquare, discouragedMovePenalty,
                NO_DEVELOPED_SOURCE_SQUARES, 0);
    }

    public List<String> getBestMoves(int depth, String discouragedSourceSquare, int discouragedMovePenalty,
                                     List<String> developedSourceSquares, int varietyDevelopmentBonus) {
        return getBestMoves(depth, Long.MAX_VALUE, discouragedSourceSquare, discouragedMovePenalty,
                developedSourceSquares, varietyDevelopmentBonus);
    }

    private List<String> getBestMoves(int depth, long searchDeadlineMillis,
                                      String discouragedSourceSquare, int discouragedMovePenalty,
                                      List<String> developedSourceSquares, int varietyDevelopmentBonus) {
        if (depth < 1) {
            throw new IllegalArgumentException("Depth must be at least 1");
        }

        checkSearchDeadline(searchDeadlineMillis);
        int[] legalMoves = generateLegalMoves();
        if (legalMoves.length == 0) {
            return new ArrayList<>();
        }

        List<String> bestMoves = new ArrayList<>();
        List<MoveEvaluation> moveEvaluations = getMoveEvaluations(depth, searchDeadlineMillis,
                discouragedSourceSquare, discouragedMovePenalty, developedSourceSquares, varietyDevelopmentBonus);
        int bestEvaluation = moveEvaluations.get(0).evaluation;

        for (MoveEvaluation moveEvaluation : moveEvaluations) {
            String move = moveEvaluation.move;
            int evaluation = moveEvaluation.evaluation;
            if (whiteToMove) {
                if (evaluation > bestEvaluation) {
                    bestEvaluation = evaluation;
                    bestMoves.clear();
                    bestMoves.add(move);
                } else if (evaluation == bestEvaluation) {
                    bestMoves.add(move);
                }
            } else if (evaluation < bestEvaluation) {
                bestEvaluation = evaluation;
                bestMoves.clear();
                bestMoves.add(move);
            } else if (evaluation == bestEvaluation) {
                bestMoves.add(move);
            }
        }

        return bestMoves;
    }

    private List<MoveEvaluation> getStaticMoveEvaluations() {
        int[] legalMoves = generateLegalMoves();
        List<MoveEvaluation> evaluations = new ArrayList<>();
        for (int move : legalMoves) {
            UndoInfo undo = makeMove(move);
            int evaluation = getMaterialEvaluationCentipawns();
            unmakeMove(undo);
            evaluation = applyRepetitionPenalty(move, evaluation);
            evaluations.add(new MoveEvaluation(moveToString(move), evaluation));
        }
        evaluations.sort(whiteToMove
                ? Comparator.comparingInt(MoveEvaluation::getEvaluation).reversed()
                : Comparator.comparingInt(MoveEvaluation::getEvaluation));
        return evaluations;
    }

    private List<MoveEvaluation> getMoveEvaluations(int depth, long searchDeadlineMillis,
                                                    String discouragedSourceSquare, int discouragedMovePenalty,
                                                    List<String> developedSourceSquares,
                                                    int varietyDevelopmentBonus) {
        return getMoveEvaluations(depth, searchDeadlineMillis, discouragedSourceSquare,
                discouragedMovePenalty, developedSourceSquares, varietyDevelopmentBonus, 0, false);
    }

    private List<MoveEvaluation> getMoveEvaluations(int depth, long searchDeadlineMillis,
                                                    String discouragedSourceSquare, int discouragedMovePenalty,
                                                    List<String> developedSourceSquares,
                                                    int varietyDevelopmentBonus,
                                                    int aspirationCenter, boolean aspirationEnabled) {
        checkSearchDeadline(searchDeadlineMillis);
        ensureTranspositionTable();
        int[] legalMoves = generateLegalMoves();
        orderMoves(legalMoves, 1);

        // Aspiration is only safe where root-move evaluations carry no adjustment
        // beyond the search score itself: no discourage/variety bonuses and not an
        // opening position (where preferences can shift an eval by tens of cp).
        // Otherwise an inferior move could be promoted past the best and we would
        // need its exact value, which a narrow window does not produce.
        boolean useAspiration = aspirationEnabled
                && depth >= 2
                && discouragedMovePenalty == 0
                && varietyDevelopmentBonus == 0
                && !isOpeningPosition()
                && Math.abs(aspirationCenter) < MATE_SCORE_THRESHOLD;
        boolean maximizing = whiteToMove;

        while (true) {
            int alpha = useAspiration ? aspirationCenter - ASPIRATION_WINDOW : -SEARCH_INFINITY;
            int beta = useAspiration ? aspirationCenter + ASPIRATION_WINDOW : SEARCH_INFINITY;
            List<MoveEvaluation> moveEvaluations = new ArrayList<>();
            int rawBest = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;

            for (int mv : legalMoves) {
                String move = moveToString(mv);
                UndoInfo undo = makeMove(mv);
                int raw = minimax(depth - 1, alpha, beta, searchDeadlineMillis, 1);
                unmakeMove(undo);
                rawBest = maximizing ? Math.max(rawBest, raw) : Math.min(rawBest, raw);

                int evaluation = applyRootMovePenalty(move, raw, discouragedSourceSquare, discouragedMovePenalty);
                evaluation = applyVarietyDevelopmentBonus(move, evaluation, developedSourceSquares, varietyDevelopmentBonus);
                evaluation = applyOpeningMovePreferences(move, evaluation);
                // Repetition-avoidance: discount moves that revisit a position already
                // seen in this game. This replaces the old random temperature as a way
                // to escape tied-eval loops in endgames (KQ-vs-K etc.) without noise.
                evaluation = applyRepetitionPenalty(mv, evaluation);
                moveEvaluations.add(new MoveEvaluation(move, evaluation));
            }

            // Re-search the whole iteration with a full window if the best move
            // landed on or outside the aspiration bounds (its value is then only a
            // bound, not exact). A single full re-search is cheaper and more robust
            // than gradual widening, which would re-search many times when the
            // score jumps (e.g. a forced mate appears in a won endgame).
            if (useAspiration) {
                boolean failedHigh = maximizing ? rawBest >= beta : rawBest <= alpha;
                boolean failedLow = maximizing ? rawBest <= alpha : rawBest >= beta;
                if (failedHigh || failedLow) {
                    useAspiration = false;
                    continue;
                }
            }

            lastRawBestScore = rawBest;
            moveEvaluations.sort(maximizing
                    ? Comparator.comparingInt(MoveEvaluation::getEvaluation).reversed()
                    : Comparator.comparingInt(MoveEvaluation::getEvaluation));
            return moveEvaluations;
        }
    }

    private int applyRepetitionPenalty(int move, int evaluation) {
        // Check the position that would result from making this move. We need a full
        // make (updates positionHistory) to get the correct resulting key, then read
        // the pre-increment count. makeMove adds 1, so count >= 2 means it was seen
        // before (i.e. we'd be repeating); count == 1 means first visit (no penalty).
        UndoInfo undo = makeMove(move);
        int visits = positionHistory.getOrDefault(getPositionKey(), 0);
        unmakeMove(undo);
        if (visits < 2) return evaluation;
        // Penalise a move that revisits a position. The engine still plays it if
        // every other option scores worse, but it won't repeat by default.
        int penalty = (visits - 1) * 50;
        return whiteToMove ? evaluation - penalty : evaluation + penalty;
    }

    private int applyRootMovePenalty(String move, int evaluation,
                                     String discouragedSourceSquare, int discouragedMovePenalty) {
        if (discouragedSourceSquare == null || discouragedMovePenalty <= 0
                || !move.startsWith(discouragedSourceSquare)) {
            return evaluation;
        }

        return whiteToMove ? evaluation - discouragedMovePenalty : evaluation + discouragedMovePenalty;
    }

    private int applyVarietyDevelopmentBonus(String move, int evaluation,
                                             List<String> developedSourceSquares, int varietyDevelopmentBonus) {
        if (developedSourceSquares == null || varietyDevelopmentBonus <= 0) {
            return evaluation;
        }

        String sourceSquare = move.substring(0, 2);
        if (developedSourceSquares.contains(sourceSquare)) {
            return evaluation;
        }

        char movingPiece = getPieceAt(toSquareIndex(sourceSquare));
        if (isPawn(movingPiece)) {
            return evaluation;
        }

        return whiteToMove ? evaluation + varietyDevelopmentBonus : evaluation - varietyDevelopmentBonus;
    }

    private int applyOpeningMovePreferences(String move, int evaluation) {
        if (!isOpeningPosition()) {
            return evaluation;
        }

        int adjustment = getOpeningMovePreference(move);
        return whiteToMove ? evaluation + adjustment : evaluation - adjustment;
    }

    private int getOpeningMovePreference(String move) {
        int fromIndex = toSquareIndex(move.substring(0, 2));
        int toIndex = toSquareIndex(move.substring(2, 4));
        char movingPiece = getPieceAt(fromIndex);
        int adjustment = 0;

        if (isPawn(movingPiece) && isCenterPawnAdvance(fromIndex, toIndex)) {
            adjustment += OPENING_CENTER_PAWN_BONUS;
        }

        if (isPawn(movingPiece)) {
            adjustment += getCenterControlGainedByPawnMove(movingPiece, fromIndex, toIndex)
                    * OPENING_CENTER_PAWN_CONTROL_BONUS;
        }

        if (isMinorPiece(movingPiece)) {
            if (otherStartingMinorPiecesRemain(movingPiece, fromIndex)) {
                adjustment -= UNDEVELOPED_MINOR_PENALTY;
            }
        }

        return adjustment;
    }

    private boolean isOpeningPosition() {
        return fullmoveNumber <= OPENING_MOVE_LIMIT;
    }

    private boolean isMinorPiece(char piece) {
        return isKnight(piece) || isBishop(piece);
    }

    private boolean isCenterPawnAdvance(int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        return (fromFile == 3 || fromFile == 4) && isCenterSquare(toIndex);
    }

    private int getCenterControlGainedByPawnMove(char movingPiece, int fromIndex, int toIndex) {
        int before = countCenterSquaresAttackedByPawn(movingPiece, fromIndex);
        int after = countCenterSquaresAttackedByPawn(movingPiece, toIndex);
        return Math.max(0, after - before);
    }

    private int countCenterSquaresAttackedByPawn(char pawn, int fromIndex) {
        int file = fromIndex % 8;
        int rank = fromIndex / 8;
        int forwardStep = Character.isUpperCase(pawn) ? 1 : -1;
        int attackedRank = rank + forwardStep;
        int count = 0;

        if (attackedRank < 0 || attackedRank > 7) {
            return 0;
        }

        for (int fileOffset : new int[]{-1, 1}) {
            int attackedFile = file + fileOffset;
            if (attackedFile < 0 || attackedFile > 7) {
                continue;
            }

            if (isCenterSquare(attackedRank * 8 + attackedFile)) {
                count++;
            }
        }

        return count;
    }

    private boolean otherStartingMinorPiecesRemain(char movingPiece, int fromIndex) {
        boolean whitePiece = Character.isUpperCase(movingPiece);
        int[] startingSquares = whitePiece ? WHITE_MINOR_START_SQUARES : BLACK_MINOR_START_SQUARES;

        for (int startingSquare : startingSquares) {
            if (startingSquare == fromIndex) {
                continue;
            }

            char piece = getPieceAt(startingSquare);
            if (piece != '.' && Character.isUpperCase(piece) == whitePiece && isMinorPiece(piece)) {
                return true;
            }
        }

        return false;
    }

    public List<String> getLegalMoves() {
        return legalMovesAsStrings();
    }

    // Bridges the int[] generator to the String-based public API and SAN code.
    private List<String> legalMovesAsStrings() {
        int[] moves = generateLegalMoves();
        List<String> result = new ArrayList<>(moves.length);
        for (int move : moves) {
            result.add(moveToString(move));
        }
        return result;
    }

    public String toSan(String move) {
        String resolvedMove = resolveMoveInput(move, false);
        if (resolvedMove == null) {
            throw new IllegalArgumentException("Illegal move: " + move);
        }
        List<String> legalMoves = legalMovesAsStrings();
        return toSan(resolvedMove, legalMoves);
    }

    public boolean isCentralPawnMove(String move) {
        String resolvedMove = resolveMoveInput(move, false);
        if (resolvedMove == null) {
            return false;
        }

        int fromIndex = toSquareIndex(resolvedMove.substring(0, 2));
        int fromFile = fromIndex % 8;
        return isPawn(getPieceAt(fromIndex)) && (fromFile == 3 || fromFile == 4);
    }

    public boolean isCurrentPlayerInCheck() {
        return isKingInCheck(whiteToMove);
    }

    public boolean isCheckmate() {
        return isCurrentPlayerInCheck() && generateLegalMoves().length == 0;
    }

    public boolean isStalemate() {
        return !isCurrentPlayerInCheck() && generateLegalMoves().length == 0;
    }

    public boolean isDrawByInsufficientMaterial() {
        if (whitePawns != 0 || blackPawns != 0
                || whiteRooks != 0 || blackRooks != 0
                || whiteQueen != 0 || blackQueen != 0) {
            return false;
        }

        int whiteKnightsCount = Long.bitCount(whiteKnights);
        int blackKnightsCount = Long.bitCount(blackKnights);
        int whiteBishopsCount = Long.bitCount(whiteBishops);
        int blackBishopsCount = Long.bitCount(blackBishops);
        int whiteMinorCount = whiteKnightsCount + whiteBishopsCount;
        int blackMinorCount = blackKnightsCount + blackBishopsCount;

        if (whiteMinorCount == 0 && blackMinorCount == 0) {
            return true;
        }

        if (whiteMinorCount == 1 && blackMinorCount == 0) {
            return true;
        }

        if (whiteMinorCount == 0 && blackMinorCount == 1) {
            return true;
        }

        if (whiteKnightsCount == 0 && blackKnightsCount == 0
                && whiteBishopsCount == 1 && blackBishopsCount == 1) {
            return bishopsOnSameColor();
        }

        return false;
    }

    public boolean isDrawByThreefoldRepetition() {
        return positionHistory.getOrDefault(getPositionKey(), 0) >= 3;
    }

    public boolean isDrawByFiftyMoveRule() {
        return halfmoveClock >= 100;
    }

    public boolean isDraw() {
        return isDrawByInsufficientMaterial()
                || isDrawByThreefoldRepetition()
                || isDrawByFiftyMoveRule()
                || isStalemate();
    }

    public String getDrawReason() {
        if (isStalemate()) {
            return "Stalemate.";
        }
        if (isDrawByInsufficientMaterial()) {
            return "Draw by insufficient material.";
        }
        if (isDrawByThreefoldRepetition()) {
            return "Draw by threefold repetition.";
        }
        if (isDrawByFiftyMoveRule()) {
            return "Draw by fifty-move rule.";
        }
        return null;
    }

    // ---- Syzygy endgame tablebase probing ----
    // True when the loaded tablebases cover this position: tables ready, no castling
    // rights (tablebase positions never have them), and few enough pieces.
    private boolean tablebaseEligible() {
        if (!SyzygyTablebase.isReady()) {
            return false;
        }
        for (int i = 0; i < castlingRights.length(); i++) {
            char c = castlingRights.charAt(i);
            if (c == 'K' || c == 'Q' || c == 'k' || c == 'q') {
                return false;
            }
        }
        if (Long.bitCount(getWhitePieces() | getBlackPieces()) > SyzygyTablebase.maxPieces()) {
            return false;
        }
        // Tablebases contain only legal positions. A position where the side NOT to
        // move is in check is illegal and has no tablebase encoding; probing it would
        // crash the native prober. This never arises from legal search, but a GUI can
        // load an arbitrary FEN, so guard against it.
        if (isKingInCheck(!whiteToMove)) {
            return false;
        }
        return true;
    }

    private int tablebaseEnPassantSquare() {
        String ep = getEffectiveEnPassantTarget();
        return ep.equals("-") ? 0 : toSquareIndex(ep);
    }

    // WDL probe mapped to a White-positive score, or NO_TB_VALUE if no result.
    // Faster wins (smaller ply) score slightly higher so the search still favours
    // progress; exact optimal play in won positions is enforced at the root by DTZ.
    private int probeTablebaseWdlScore(int ply) {
        int wdl = SyzygyTablebase.probeWdl(getWhitePieces(), getBlackPieces(),
                whiteKing | blackKing, whiteQueen | blackQueen, whiteRooks | blackRooks,
                whiteBishops | blackBishops, whiteKnights | blackKnights, whitePawns | blackPawns,
                tablebaseEnPassantSquare(), whiteToMove);
        if (wdl == SyzygyTablebase.FAILED) {
            return NO_TB_VALUE;
        }
        int sideToMoveSign = whiteToMove ? 1 : -1;     // convert stm-relative -> White-positive
        return switch (wdl) {
            case SyzygyTablebase.WDL_WIN -> sideToMoveSign * (TB_WIN_SCORE - ply);
            case SyzygyTablebase.WDL_LOSS -> sideToMoveSign * -(TB_WIN_SCORE - ply);
            default -> 0;                              // draw, cursed win, blessed loss
        };
    }

    // Root DTZ probe: returns a SearchReport whose single best move is the
    // tablebase-optimal move (perfect play, 50-move-rule aware), or null to fall
    // back to the normal search.
    private SearchReport probeTablebaseRootReport() {
        int[] r = SyzygyTablebase.probeRoot(getWhitePieces(), getBlackPieces(),
                whiteKing | blackKing, whiteQueen | blackQueen, whiteRooks | blackRooks,
                whiteBishops | blackBishops, whiteKnights | blackKnights, whitePawns | blackPawns,
                halfmoveClock, tablebaseEnPassantSquare(), whiteToMove);
        if (r == null) {
            return null;
        }
        int wdl = r[0], dtz = r[1], from = r[2], to = r[3], promo = r[4];
        int move = packMove(from, to, promo);

        // Safety: only trust the move if it is actually legal here.
        boolean legal = false;
        for (int m : generateLegalMoves()) {
            if (m == move) {
                legal = true;
                break;
            }
        }
        if (!legal) {
            return null;
        }

        int sideToMoveSign = whiteToMove ? 1 : -1;
        int score = switch (wdl) {
            case SyzygyTablebase.WDL_WIN -> sideToMoveSign * (TB_WIN_SCORE - Math.abs(dtz));
            case SyzygyTablebase.WDL_LOSS -> sideToMoveSign * -(TB_WIN_SCORE - Math.abs(dtz));
            default -> 0;
        };
        SearchReport report = new SearchReport();
        List<MoveEvaluation> single = new ArrayList<>();
        single.add(new MoveEvaluation(moveToString(move), score));
        report.recordCompletedDepth(0, single);
        return report;
    }

    private int minimax(int depth, int alpha, int beta) {
        return minimax(depth, alpha, beta, Long.MAX_VALUE);
    }

    private int minimax(int depth, int alpha, int beta, long searchDeadlineMillis) {
        return minimax(depth, alpha, beta, searchDeadlineMillis, 1);
    }

    private int minimax(int depth, int alpha, int beta, long searchDeadlineMillis, int ply) {
        checkSearchDeadline(searchDeadlineMillis);

        // Path/state-based draws are cheap (no move generation) and must be tested
        // before any transposition cutoff so repetition is detected on this path.
        // Stalemate (the move-generating part of the old isDraw()) is deferred to
        // the single move generation below.
        if (isDrawByInsufficientMaterial() || isDrawByThreefoldRepetition() || isDrawByFiftyMoveRule()) {
            return 0;
        }

        boolean inCheck = isCurrentPlayerInCheck();
        if (inCheck) {
            // Check extension: search one ply deeper when in check.
            depth++;
        }

        if (depth <= 0) {
            // Leaf node. Detect checkmate/stalemate here so behaviour matches the
            // pre-change top-of-node isCheckmate()/isDraw() that ran before the
            // quiescence gate. This is one generation, exactly as before; quiescence
            // is left untouched.
            int[] leafMoves = generateLegalMoves();
            if (leafMoves.length == 0) {
                if (inCheck) {
                    int mateScore = CHECKMATE_SCORE - ply;
                    return whiteToMove ? -mateScore : mateScore;
                }
                return 0;
            }
            return quiescenceSearch(alpha, beta, searchDeadlineMillis, ply);
        }

        long zobristKey = positionZobristKey;
        if (VERIFY_ZOBRIST_IN_SEARCH && zobristKey != computeZobristKey()) {
            throw new IllegalStateException("search-path zobrist mismatch at " + toFen());
        }
        TranspositionEntry ttEntry = probeTransposition(zobristKey);
        int ttMove = ttEntry != null ? ttEntry.bestMove : NO_MOVE;
        int originalAlpha = alpha;
        int originalBeta = beta;
        int bestMove = NO_MOVE;

        // Probe and cut BEFORE generating moves: a usable entry means we never pay
        // the ~per-node move-generation cost on this node at all.
        if (ttEntry != null && ttEntry.depth >= depth) {
            int ttValue = adjustMateScoreFromStorage(ttEntry.value, ply);
            if (ttEntry.flag == TRANSPOSITION_EXACT) {
                return ttValue;
            }
            if (ttEntry.flag == TRANSPOSITION_ALPHA && ttValue <= alpha) {
                return ttValue;
            }
            if (ttEntry.flag == TRANSPOSITION_BETA && ttValue >= beta) {
                return ttValue;
            }
        }

        // Tablebase WDL cutoff: a perfect win/draw/loss verdict for any covered
        // (<= maxPieces, no-castling) position, returned before generating moves so
        // entire endgame subtrees collapse to a single lookup. Guarded cheaply by
        // isReady() so it is a no-op when no tablebases are loaded.
        if (tablebaseEligible()) {
            int tbScore = probeTablebaseWdlScore(ply);
            if (tbScore != NO_TB_VALUE) {
                tablebaseHits++;
                return tbScore;
            }
        }

        boolean maximizing = whiteToMove;

        // Null-move pruning: hand the side to move a free pass and search to a
        // reduced depth. If the position is still good enough to cause a cutoff
        // even after doing nothing, the real position certainly is, so prune.
        // Skipped when in check, near a mate bound, or without non-pawn material
        // (where passing could be better than any move, i.e. zugzwang).
        boolean betaIsMate = Math.abs(beta) >= MATE_SCORE_THRESHOLD && Math.abs(beta) <= CHECKMATE_SCORE;
        boolean alphaIsMate = Math.abs(alpha) >= MATE_SCORE_THRESHOLD && Math.abs(alpha) <= CHECKMATE_SCORE;
        if (!inCheck && depth >= 3 && !betaIsMate && !alphaIsMate && hasNonPawnMaterial(maximizing)) {
            int nullReduction = 2;
            String nullUndo = makeNullMove();
            int nullScore;
            if (maximizing) {
                nullScore = minimax(depth - 1 - nullReduction, beta - 1, beta, searchDeadlineMillis, ply + 1);
            } else {
                nullScore = minimax(depth - 1 - nullReduction, alpha, alpha + 1, searchDeadlineMillis, ply + 1);
            }
            unmakeNullMove(nullUndo);
            if (maximizing && nullScore >= beta) {
                return beta;
            }
            if (!maximizing && nullScore <= alpha) {
                return alpha;
            }
        }

        int[] legalMoves = generateLegalMoves();
        if (legalMoves.length == 0) {
            // No legal moves: checkmate if in check, otherwise stalemate. This was
            // previously detected by isCheckmate()/isDraw() at the node top; doing
            // it from the single generation here avoids the extra move-gen pass.
            if (inCheck) {
                int mateScore = CHECKMATE_SCORE - ply;
                return whiteToMove ? -mateScore : mateScore;
            }
            return 0;
        }
        orderMoves(legalMoves, ply, ttMove);
        int bestEvaluation = maximizing ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int moveIndex = 0;

        for (int move : legalMoves) {
            boolean quiet = isQuietMove(move);
            UndoInfo undo = makeMove(move);
            boolean givesCheck = isCurrentPlayerInCheck();

            int evaluation;
            if (moveIndex == 0) {
                // Principal variation: the first (best-ordered) move gets a full
                // window, full depth search.
                evaluation = minimax(depth - 1, alpha, beta, searchDeadlineMillis, ply + 1);
            } else {
                // Late move reduction: reduce quiet, non-checking, late moves.
                int reduction = 0;
                if (depth >= 3 && moveIndex >= 3 && !inCheck && quiet && !givesCheck) {
                    reduction = moveIndex >= 6 ? 2 : 1;
                    if (reduction > depth - 2) {
                        reduction = depth - 2;
                    }
                    if (reduction < 0) {
                        reduction = 0;
                    }
                }

                // PVS: probe with a null window (possibly reduced). Re-search at
                // full depth and full window only if the move beats the bound.
                if (maximizing) {
                    evaluation = minimax(depth - 1 - reduction, alpha, alpha + 1, searchDeadlineMillis, ply + 1);
                    if (evaluation > alpha) {
                        evaluation = minimax(depth - 1, alpha, beta, searchDeadlineMillis, ply + 1);
                    }
                } else {
                    evaluation = minimax(depth - 1 - reduction, beta - 1, beta, searchDeadlineMillis, ply + 1);
                    if (evaluation < beta) {
                        evaluation = minimax(depth - 1, alpha, beta, searchDeadlineMillis, ply + 1);
                    }
                }
            }
            unmakeMove(undo);

            if (maximizing) {
                if (evaluation > bestEvaluation) {
                    bestEvaluation = evaluation;
                    bestMove = move;
                }
                alpha = Math.max(alpha, bestEvaluation);
            } else {
                if (evaluation < bestEvaluation) {
                    bestEvaluation = evaluation;
                    bestMove = move;
                }
                beta = Math.min(beta, bestEvaluation);
            }

            if (alpha >= beta) {
                if (quiet) {
                    addKillerMove(move, ply);
                }
                break;
            }

            if (quiet) {
                addHistoryHeuristic(move, depth);
            }
            moveIndex++;
        }

        int flag = TRANSPOSITION_EXACT;
        if (bestEvaluation <= originalAlpha) {
            flag = TRANSPOSITION_ALPHA;
        } else if (bestEvaluation >= originalBeta) {
            flag = TRANSPOSITION_BETA;
        }
        storeTransposition(zobristKey, depth, adjustMateScoreForStorage(bestEvaluation, ply), flag, bestMove);

        return bestEvaluation;
    }

    private boolean hasNonPawnMaterial(boolean white) {
        if (white) {
            return (whiteKnights | whiteBishops | whiteRooks | whiteQueen) != 0;
        }
        return (blackKnights | blackBishops | blackRooks | blackQueen) != 0;
    }

    private String makeNullMove() {
        String savedEnPassant = enPassantTarget;
        // Remove the old effective-ep contribution while the pre-null state is
        // still in place, then toggle side. The new ep target is "-", which
        // contributes nothing, so there is nothing further to add.
        positionZobristKey ^= epFileKey();
        enPassantTarget = "-";
        whiteToMove = !whiteToMove;
        positionZobristKey ^= ZOBRIST_SIDE;
        return savedEnPassant;
    }

    private void unmakeNullMove(String savedEnPassant) {
        whiteToMove = !whiteToMove;
        enPassantTarget = savedEnPassant;
        // State is restored to the pre-null position, so epFileKey() now
        // recomputes exactly the contribution removed in makeNullMove.
        positionZobristKey ^= ZOBRIST_SIDE;
        positionZobristKey ^= epFileKey();
    }

    private int quiescenceSearch(int alpha, int beta, long searchDeadlineMillis) {
        return quiescenceSearch(alpha, beta, searchDeadlineMillis, 1);
    }

    private int quiescenceSearch(int alpha, int beta, long searchDeadlineMillis, int ply) {
        checkSearchDeadline(searchDeadlineMillis);

        long key = positionZobristKey;
        int origAlpha = alpha;
        int origBeta = beta;
        if (qttKeys == null) {
            qttKeys = new long[QTT_SIZE];
            qttValues = new int[QTT_SIZE];
            qttFlags = new byte[QTT_SIZE];
        } else {
            int qi = (int) (key & QTT_MASK);
            if (qttKeys[qi] == key) {
                int v = adjustMateScoreFromStorage(qttValues[qi], ply);
                int f = qttFlags[qi];
                if (f == TRANSPOSITION_EXACT
                        || (f == TRANSPOSITION_ALPHA && v <= alpha)
                        || (f == TRANSPOSITION_BETA && v >= beta)) {
                    return v;
                }
            }
        }

        int standPat = cachedEvaluation();
        int delta = QUEEN_VALUE + ROOK_VALUE;
        if (whiteToMove) {
            if (standPat + delta <= alpha) {
                return alpha;
            }
            if (standPat >= beta) {
                return beta;
            }
            alpha = Math.max(alpha, standPat);
        } else {
            if (standPat - delta >= beta) {
                return beta;
            }
            if (standPat <= alpha) {
                return alpha;
            }
            beta = Math.min(beta, standPat);
        }

        int[] legalMoves = generateLegalMoves();
        int[] quiescenceMoves;
        int quiescenceCount;
        if (isCurrentPlayerInCheck()) {
            quiescenceMoves = legalMoves;
            quiescenceCount = legalMoves.length;
        } else {
            quiescenceMoves = new int[legalMoves.length];
            quiescenceCount = 0;
            for (int move : legalMoves) {
                if (isQuiescenceMove(move)) {
                    quiescenceMoves[quiescenceCount++] = move;
                }
            }
        }

        if (quiescenceCount == 0) {
            storeQuiescence(key, standPat, origAlpha, origBeta, ply);
            return standPat;
        }

        orderQuiescenceMoves(quiescenceMoves, quiescenceCount);
        int bestEvaluation = standPat;

        for (int i = 0; i < quiescenceCount; i++) {
            int move = quiescenceMoves[i];
            UndoInfo undo = makeMove(move);  // must maintain hash: eval cache / qTT key off it
            int evaluation = quiescenceSearch(alpha, beta, searchDeadlineMillis, ply + 1);
            unmakeMove(undo);
            if (whiteToMove) {
                bestEvaluation = Math.max(bestEvaluation, evaluation);
                alpha = Math.max(alpha, bestEvaluation);
            } else {
                bestEvaluation = Math.min(bestEvaluation, evaluation);
                beta = Math.min(beta, bestEvaluation);
            }

            if (alpha >= beta) {
                break;
            }
        }

        storeQuiescence(key, bestEvaluation, origAlpha, origBeta, ply);
        return bestEvaluation;
    }

    private boolean isQuiescenceMove(int move) {
        return isCaptureMove(move) || isPromotionMove(move);
    }

    private boolean isCaptureMove(int move) {
        int fromIndex = moveFromSq(move);
        int toIndex = moveToSq(move);
        char movingPiece = getPieceAt(fromIndex);
        char capturedPiece = getPieceAt(toIndex);
        if (capturedPiece != '.') {
            return true;
        }
        return isEnPassantCapture(movingPiece, fromIndex, toIndex, capturedPiece);
    }

    private boolean isPromotionMove(int move) {
        return movePromoCode(move) != 0;
    }

    // Shared scratch for move-ordering scores. Safe to share because ordering is
    // non-reentrant: each sort completes fully before the search recurses.
    private final int[] sortScores = new int[256];

    private void orderMoves(int[] moves, int ply) {
        TranspositionEntry entry = probeTransposition(positionZobristKey);
        orderMoves(moves, ply, entry != null ? entry.bestMove : NO_MOVE);
    }

    private void orderMoves(int[] moves, int ply, int ttMove) {
        int count = moves.length;
        for (int i = 0; i < count; i++) {
            sortScores[i] = getMoveOrderingScore(moves[i], ttMove, ply);
        }
        stableSortByScoreDescending(moves, count, sortScores);
    }

    private void orderQuiescenceMoves(int[] moves, int count) {
        for (int i = 0; i < count; i++) {
            sortScores[i] = getQuiescenceMoveScore(moves[i]);
        }
        stableSortByScoreDescending(moves, count, sortScores);
    }

    // Stable descending sort of moves[0..count) by parallel scores. Stable so that
    // equal-scored moves keep generation order, matching the previous List.sort.
    private void stableSortByScoreDescending(int[] moves, int count, int[] scores) {
        for (int i = 1; i < count; i++) {
            int mv = moves[i];
            int sc = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < sc) {
                moves[j + 1] = moves[j];
                scores[j + 1] = scores[j];
                j--;
            }
            moves[j + 1] = mv;
            scores[j + 1] = sc;
        }
    }

    private int getQuiescenceMoveScore(int move) {
        int fromIndex = moveFromSq(move);
        int toIndex = moveToSq(move);
        char movingPiece = getPieceAt(fromIndex);
        char capturedPiece = getCapturedPieceForOrdering(move, movingPiece, fromIndex, toIndex);
        int score = 0;

        if (capturedPiece != '.') {
            score += 1000 + getPieceValue(capturedPiece) * 10 - getPieceValue(movingPiece);
        }
        if (movePromoCode(move) != 0) {
            score += 900 + getPieceValue(promoCharFromCode(movePromoCode(move)));
        }
        return score;
    }

    private int getMoveOrderingScore(int move, int principalMove, int ply) {
        int fromIndex = moveFromSq(move);
        int toIndex = moveToSq(move);
        char movingPiece = getPieceAt(fromIndex);
        char capturedPiece = getCapturedPieceForOrdering(move, movingPiece, fromIndex, toIndex);
        int score = 0;

        if (move == principalMove && principalMove != NO_MOVE) {
            score += 5_000_000;
        }

        if (capturedPiece != '.') {
            score += 800_000 + getCaptureOrderingScore(movingPiece, capturedPiece);
        } else if (movePromoCode(move) != 0) {
            score += 700_000 + getPieceValue(promoCharFromCode(movePromoCode(move)));
        } else {
            score += getQuietMoveOrderingScore(movingPiece, fromIndex, toIndex);
            score += getKillerScore(move, ply);
            score += historyHeuristic[fromIndex][toIndex];
        }

        return score;
    }

    private char getCapturedPieceForOrdering(int move, char movingPiece, int fromIndex, int toIndex) {
        char capturedPiece = getPieceAt(toIndex);
        if (!isEnPassantCapture(movingPiece, fromIndex, toIndex, capturedPiece)) {
            return capturedPiece;
        }

        int capturedPawnIndex = toIndex + (Character.isUpperCase(movingPiece) ? -8 : 8);
        return getPieceAt(capturedPawnIndex);
    }

    private int getCaptureOrderingScore(char movingPiece, char capturedPiece) {
        if (capturedPiece == '.') {
            return 0;
        }

        return getPieceValue(capturedPiece) * 10 - getPieceValue(movingPiece);
    }

    private int getQuietMoveOrderingScore(char movingPiece, int fromIndex, int toIndex) {
        int score = 0;
        if (!isPawn(movingPiece) && isBackRank(fromIndex, Character.isUpperCase(movingPiece))) {
            score += 50;
        }
        if (isCenterSquare(toIndex)) {
            score += 25;
        }
        return score;
    }

    private boolean isQuietMove(int move) {
        return !isCaptureMove(move) && !isPromotionMove(move);
    }

    private void addKillerMove(int move, int ply) {
        if (ply < 1 || ply >= killerMoves1.length) {
            return;
        }
        if (move == killerMoves1[ply]) {
            return;
        }
        if (move == killerMoves2[ply]) {
            killerMoves1[ply] = killerMoves2[ply];
            killerMoves2[ply] = NO_MOVE;
            return;
        }
        killerMoves2[ply] = killerMoves1[ply];
        killerMoves1[ply] = move;
    }

    private int getKillerScore(int move, int ply) {
        if (ply < 1 || ply >= killerMoves1.length) {
            return 0;
        }
        if (move == killerMoves1[ply]) {
            return 200_000;
        }
        if (move == killerMoves2[ply]) {
            return 150_000;
        }
        return 0;
    }

    private void addHistoryHeuristic(int move, int depth) {
        historyHeuristic[moveFromSq(move)][moveToSq(move)] += depth * depth;
    }

    private int getBestThreatScoreForPreviousMove(String previousMove) {
        int toIndex = toSquareIndex(previousMove.substring(2, 4));
        char movedPiece = getPieceAt(toIndex);
        if (movedPiece == '.') {
            return 0;
        }

        boolean movedPieceWhite = Character.isUpperCase(movedPiece);
        long opponentPieces = movedPieceWhite ? getBlackPieces() : getWhitePieces();
        int bestThreatScore = 0;
        for (int targetIndex = 0; targetIndex < 64; targetIndex++) {
            long targetMask = 1L << targetIndex;
            if ((opponentPieces & targetMask) == 0 || !pieceAttacksSquare(movedPiece, toIndex, targetIndex)) {
                continue;
            }

            bestThreatScore = Math.max(bestThreatScore,
                    getPieceValue(getPieceAt(targetIndex)) * 10 - getPieceValue(movedPiece));
        }

        return bestThreatScore;
    }

    private boolean pieceAttacksSquare(char piece, int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        int toFile = toIndex % 8;
        int toRank = toIndex / 8;

        return switch (Character.toLowerCase(piece)) {
            case 'p' -> Math.abs(toFile - fromFile) == 1
                    && toRank - fromRank == (Character.isUpperCase(piece) ? 1 : -1);
            case 'n' -> {
                int fileDelta = Math.abs(toFile - fromFile);
                int rankDelta = Math.abs(toRank - fromRank);
                yield (fileDelta == 1 && rankDelta == 2) || (fileDelta == 2 && rankDelta == 1);
            }
            case 'b' -> Math.abs(toFile - fromFile) == Math.abs(toRank - fromRank)
                    && isPathClear(fromIndex, toIndex);
            case 'r' -> (fromFile == toFile || fromRank == toRank) && isPathClear(fromIndex, toIndex);
            case 'q' -> ((fromFile == toFile || fromRank == toRank)
                    || Math.abs(toFile - fromFile) == Math.abs(toRank - fromRank))
                    && isPathClear(fromIndex, toIndex);
            case 'k' -> Math.max(Math.abs(toFile - fromFile), Math.abs(toRank - fromRank)) == 1;
            default -> false;
        };
    }

    private boolean isBackRank(int squareIndex, boolean whitePiece) {
        int rank = squareIndex / 8;
        return whitePiece ? rank == 0 : rank == 7;
    }

    private boolean isCenterSquare(int squareIndex) {
        for (int centerSquare : CENTER_SQUARES) {
            if (squareIndex == centerSquare) {
                return true;
            }
        }
        return false;
    }

    private int getPieceValue(char piece) {
        return switch (Character.toLowerCase(piece)) {
            case 'p' -> PAWN_VALUE;
            case 'n' -> KNIGHT_VALUE;
            case 'b' -> BISHOP_VALUE;
            case 'r' -> ROOK_VALUE;
            case 'q' -> QUEEN_VALUE;
            case 'k' -> KING_VALUE;
            default -> 0;
        };
    }

    private void checkSearchDeadline(long searchDeadlineMillis) {
        if (searchAbortRequested || System.currentTimeMillis() > searchDeadlineMillis) {
            throw new SearchTimeoutException();
        }
    }

    // Requests that an in-progress search stop as soon as possible (used by the
    // UCI "stop"/"quit" handlers). The search returns the best line found so far
    // via the normal timeout-unwind path. No-op if no search is running.
    public void requestSearchAbort() {
        searchAbortRequested = true;
    }

    // Clears a pending abort request. Callers must clear before launching a new
    // search (on the launching thread, so it cannot race with a subsequent
    // requestSearchAbort) so a stale stop does not cancel the next search.
    public void clearSearchAbort() {
        searchAbortRequested = false;
    }

    private static class SearchTimeoutException extends RuntimeException {
    }

    private static final class UndoInfo {
        private int fromIndex;
        private int toIndex;
        private char movingPiece;
        private char promotion;
        private char capturedPiece;
        private int capturedSquare;
        private boolean enPassant;
        private boolean castling;
        private String prevCastlingRights;
        private String prevEnPassantTarget;
        private int prevHalfmoveClock;
        private int prevFullmoveNumber;
        private String positionKey;
        private long prevZobristKey;   // full key before the move; restored verbatim on unmake
    }

    private static class TranspositionEntry {
        private final long key;
        private final int depth;
        private final int value;
        private final int flag;
        private final int bestMove;
        private final int age;

        private TranspositionEntry(long key, int depth, int value, int flag, int bestMove, int age) {
            this.key = key;
            this.depth = depth;
            this.value = value;
            this.flag = flag;
            this.bestMove = bestMove;
            this.age = age;
        }
    }

    public static class MoveEvaluation {
        private final String move;
        private final int evaluation;

        private MoveEvaluation(String move, int evaluation) {
            this.move = move;
            this.evaluation = evaluation;
        }

        public String getMove() {
            return move;
        }

        public int getEvaluation() {
            return evaluation;
        }
    }

    public static class SearchReport {
        private final List<Integer> completedDepths = new ArrayList<>();
        private List<MoveEvaluation> topMoves = new ArrayList<>();
        private boolean timedOut;

        private void recordCompletedDepth(int depth, List<MoveEvaluation> moveEvaluations) {
            completedDepths.add(depth);
            topMoves = new ArrayList<>(moveEvaluations);
        }

        private void setFallbackMoves(List<MoveEvaluation> moveEvaluations) {
            // Provides a best move without recording a completed depth, so a legal
            // move is available even when no full-depth search finishes in time.
            topMoves = new ArrayList<>(moveEvaluations);
        }

        private void markTimedOut() {
            timedOut = true;
        }

        public List<Integer> getCompletedDepths() {
            return new ArrayList<>(completedDepths);
        }

        public boolean timedOut() {
            return timedOut;
        }

        public String getBestMove() {
            return topMoves.isEmpty() ? null : topMoves.get(0).getMove();
        }

        public List<MoveEvaluation> getTopMoves(int count) {
            int moveCount = Math.min(count, topMoves.size());
            return new ArrayList<>(topMoves.subList(0, moveCount));
        }
    }

    private boolean tryMove(String move, boolean verbose) {
        move = move.trim().toLowerCase();

        //UCI notation can only be 4 or 5 characters long
        if (move.length() < 4 || move.length() > 5) {
            return failMove(verbose, "Move can only be 4-5 characters long!");
        }

        //Only a-h are valid files
        if (!isValidFile(move.charAt(0)) || !isValidFile(move.charAt(2))) {
            return failMove(verbose, "Not a valid file!");
        }

        //Only 1-8 are valid ranks
        if (!isValidRank(move.charAt(1)) || !isValidRank(move.charAt(3))) {
            return failMove(verbose, "Not a valid rank!");
        }

        //Only q, n, r, and b are valid promotions
        if (move.length() == 5 && !isValidPromotionPiece(move.charAt(4))) {
            return failMove(verbose, "Not a valid promotion piece!");
        }

        String originSquare = move.substring(0, 2);
        String targetSquare = move.substring(2, 4);

        if (!hasOwnPiece(originSquare)) {
            return failMove(verbose, "Origin square must contain your own piece!");
        }

        if (hasOwnPiece(targetSquare)) {
            return failMove(verbose, "Target square cannot contain your own piece!");
        }

        boolean movingWhite = whiteToMove;
        int fromIndex = toSquareIndex(originSquare);
        int toIndex = toSquareIndex(targetSquare);
        char movingPiece = getPieceAt(fromIndex);

        if (isPawn(movingPiece) && !isLegalPawnMove(fromIndex, toIndex)) {
            return failMove(verbose, "Illegal pawn move!");
        }

        if (isRook(movingPiece) && !isLegalRookMove(fromIndex, toIndex)) {
            return failMove(verbose, "Illegal rook move!");
        }

        if (isBishop(movingPiece) && !isLegalBishopMove(fromIndex, toIndex)) {
            return failMove(verbose, "Illegal bishop move!");
        }

        if (isQueen(movingPiece) && !isLegalQueenMove(fromIndex, toIndex)) {
            return failMove(verbose, "Illegal queen move!");
        }

        if (isKnight(movingPiece) && !isLegalKnightMove(fromIndex, toIndex)) {
            return failMove(verbose, "Illegal knight move!");
        }

        if (isKing(movingPiece) && !isLegalKingMove(fromIndex, toIndex)) {
            return failMove(verbose, "Illegal king move!");
        }

        if (isPawn(movingPiece) && !isValidPromotionChoice(fromIndex, toIndex, move.length() == 5)) {
            return failMove(verbose, "Invalid promotion usage!");
        }

        char promotion = move.length() == 5 ? move.charAt(4) : NO_PROMOTION;
        UndoInfo undo = makeMove(fromIndex, toIndex, promotion);
        if (isKingInCheck(movingWhite)) {
            unmakeMove(undo);
            return failMove(verbose, "Move leaves your king in check!");
        }
        return true;
    }

    private static final char NO_PROMOTION = '\0';

    /**
     * Applies a (pseudo-)legal move to this board in place and returns the
     * information needed to reverse it with {@link #unmakeMove}. No legality
     * checking is performed: callers must pass moves that are at least
     * pseudo-legal. This replaces the previous copy-make approach (allocating a
     * whole Board plus cloning the position-history map at every node).
     */
    private UndoInfo makeMove(String move) {
        int fromIndex = toSquareIndex(move.substring(0, 2));
        int toIndex = toSquareIndex(move.substring(2, 4));
        char promotion = move.length() == 5 ? move.charAt(4) : NO_PROMOTION;
        return makeMove(fromIndex, toIndex, promotion);
    }

    private UndoInfo makeMove(String move, boolean maintainHash) {
        int fromIndex = toSquareIndex(move.substring(0, 2));
        int toIndex = toSquareIndex(move.substring(2, 4));
        char promotion = move.length() == 5 ? move.charAt(4) : NO_PROMOTION;
        return makeMove(fromIndex, toIndex, promotion, maintainHash);
    }

    private UndoInfo makeMove(int move) {
        return makeMove(moveFromSq(move), moveToSq(move), promoCharFromCode(movePromoCode(move)), true);
    }

    private UndoInfo makeMove(int move, boolean maintainHash) {
        return makeMove(moveFromSq(move), moveToSq(move), promoCharFromCode(movePromoCode(move)), maintainHash);
    }

    private UndoInfo makeMove(int fromIndex, int toIndex, char promotion) {
        return makeMove(fromIndex, toIndex, promotion, true);
    }

    // maintainHash=false skips incremental key maintenance, for make/unmake pairs
    // that never read the key between them (the king-safety legality probe in
    // moveIsFullyLegal). That probe is by far the most frequent make/unmake, so
    // skipping its hashing is what makes incremental hashing a net win.
    private UndoInfo makeMove(int fromIndex, int toIndex, char promotion, boolean maintainHash) {
        boolean movingWhite = whiteToMove;
        long fromMask = 1L << fromIndex;
        long toMask = 1L << toIndex;
        char movingPiece = getPieceAt(fromIndex);
        char capturedPiece = getPieceAt(toIndex);
        boolean enPassantCapture = isEnPassantCapture(movingPiece, fromIndex, toIndex, capturedPiece);
        boolean castlingMove = isCastlingMove(movingPiece, fromIndex, toIndex);

        UndoInfo undo = new UndoInfo();
        undo.fromIndex = fromIndex;
        undo.toIndex = toIndex;
        undo.movingPiece = movingPiece;
        undo.promotion = promotion;
        undo.enPassant = enPassantCapture;
        undo.castling = castlingMove;
        undo.prevCastlingRights = castlingRights;
        undo.prevEnPassantTarget = enPassantTarget;
        undo.prevHalfmoveClock = halfmoveClock;
        undo.prevFullmoveNumber = fullmoveNumber;
        undo.prevZobristKey = positionZobristKey;

        // Pre-move Zobrist contributions for the state terms (computed before any
        // board mutation, using the pre-move side/rights/ep). The new values are
        // read back after the mutations to form the incremental delta.
        int preCastlingIndex = maintainHash ? castlingRightsIndex() : 0;
        long preEpKey = maintainHash ? epFileKey() : 0L;

        int capturedSquare;
        if (enPassantCapture) {
            int capturedPawnIndex = toIndex + (movingWhite ? -8 : 8);
            capturedPiece = getPieceAt(capturedPawnIndex);
            clearSquare(1L << capturedPawnIndex);
            capturedSquare = capturedPawnIndex;
        } else {
            capturedSquare = toIndex;
        }
        undo.capturedPiece = capturedPiece;
        undo.capturedSquare = capturedSquare;

        clearSquare(toMask);
        movePieceOnBitboards(movingPiece, fromMask, toMask);

        if (promotion != NO_PROMOTION) {
            promotePiece(promotion, toMask);
        }

        if (castlingMove) {
            moveRookForCastling(toIndex);
        }

        updateCastlingRights(movingPiece, fromIndex, toIndex, capturedPiece);
        updateEnPassantTarget(movingPiece, fromIndex, toIndex);
        updateMoveCounters(movingPiece, capturedPiece);
        whiteToMove = !whiteToMove;

        // ---- Incremental Zobrist update ----
        // Piece terms, derived from the move's semantics so they mirror exactly
        // what computeZobristKey() would produce for the resulting position.
        if (maintainHash) {
        long keyDelta = ZOBRIST_PIECE[zobristPieceIndex(movingPiece)][fromIndex];
        if (promotion != NO_PROMOTION) {
            char promoted = movingWhite ? Character.toUpperCase(promotion) : promotion;
            keyDelta ^= ZOBRIST_PIECE[zobristPieceIndex(promoted)][toIndex];
        } else {
            keyDelta ^= ZOBRIST_PIECE[zobristPieceIndex(movingPiece)][toIndex];
        }
        if (capturedPiece != '.') {
            // capturedSquare is the en-passant pawn's square for ep, else toIndex.
            keyDelta ^= ZOBRIST_PIECE[zobristPieceIndex(capturedPiece)][capturedSquare];
        }
        if (castlingMove) {
            int rookIndex = movingWhite ? 3 : 9;
            int rookFrom;
            int rookTo;
            switch (toIndex) {
                case 6  -> { rookFrom = 7;  rookTo = 5;  }   // white O-O
                case 2  -> { rookFrom = 0;  rookTo = 3;  }   // white O-O-O
                case 62 -> { rookFrom = 63; rookTo = 61; }   // black O-O
                case 58 -> { rookFrom = 56; rookTo = 59; }   // black O-O-O
                default -> throw new IllegalStateException("Invalid castling target square");
            }
            keyDelta ^= ZOBRIST_PIECE[rookIndex][rookFrom] ^ ZOBRIST_PIECE[rookIndex][rookTo];
        }
        keyDelta ^= ZOBRIST_SIDE;                            // side always flips
        // Castling rights only change on king/rook moves and rook captures, so
        // skip the (string-scanning) index lookups on the common case.
        if (!castlingRights.equals(undo.prevCastlingRights)) {
            keyDelta ^= ZOBRIST_CASTLING[preCastlingIndex] ^ ZOBRIST_CASTLING[castlingRightsIndex()];
        }
        keyDelta ^= preEpKey ^ epFileKey();                  // ep-file changes (cheap when none)
        positionZobristKey ^= keyDelta;
        }

        String positionKey = getPositionKey();
        undo.positionKey = positionKey;
        positionHistory.merge(positionKey, 1, Integer::sum);

        return undo;
    }

    /** Reverses the most recent {@link #makeMove}, restoring exact prior state. */
    private void unmakeMove(UndoInfo undo) {
        Integer count = positionHistory.get(undo.positionKey);
        if (count != null) {
            if (count <= 1) {
                positionHistory.remove(undo.positionKey);
            } else {
                positionHistory.put(undo.positionKey, count - 1);
            }
        }

        whiteToMove = !whiteToMove;
        castlingRights = undo.prevCastlingRights;
        enPassantTarget = undo.prevEnPassantTarget;
        halfmoveClock = undo.prevHalfmoveClock;
        fullmoveNumber = undo.prevFullmoveNumber;
        positionZobristKey = undo.prevZobristKey;   // exact restore; no reverse delta needed

        long fromMask = 1L << undo.fromIndex;
        long toMask = 1L << undo.toIndex;

        if (undo.castling) {
            undoRookForCastling(undo.toIndex);
        }

        clearSquare(toMask);
        restoreCapturedPiece(undo.movingPiece, fromMask);

        if (undo.capturedPiece != '.') {
            restoreCapturedPiece(undo.capturedPiece, 1L << undo.capturedSquare);
        }
    }

    private void undoRookForCastling(int kingToIndex) {
        switch (kingToIndex) {
            case 6 -> whiteRooks = (whiteRooks & ~(1L << 5)) | (1L << 7);
            case 2 -> whiteRooks = (whiteRooks & ~(1L << 3)) | (1L << 0);
            case 62 -> blackRooks = (blackRooks & ~(1L << 61)) | (1L << 63);
            case 58 -> blackRooks = (blackRooks & ~(1L << 59)) | (1L << 56);
            default -> throw new IllegalStateException("Invalid castling target square");
        }
    }

    /**
     * Full legality test for a pseudo-legal move: validates piece movement,
     * promotion usage and king safety using make/unmake. Used to filter the
     * pseudo-legal generator output without allocating a board.
     */
    private boolean moveIsFullyLegal(int move) {
        int fromIndex = moveFromSq(move);
        int toIndex = moveToSq(move);
        char movingPiece = getPieceAt(fromIndex);

        if (isPawn(movingPiece) && !isLegalPawnMove(fromIndex, toIndex)) {
            return false;
        }
        if (isRook(movingPiece) && !isLegalRookMove(fromIndex, toIndex)) {
            return false;
        }
        if (isBishop(movingPiece) && !isLegalBishopMove(fromIndex, toIndex)) {
            return false;
        }
        if (isQueen(movingPiece) && !isLegalQueenMove(fromIndex, toIndex)) {
            return false;
        }
        if (isKnight(movingPiece) && !isLegalKnightMove(fromIndex, toIndex)) {
            return false;
        }
        if (isKing(movingPiece) && !isLegalKingMove(fromIndex, toIndex)) {
            return false;
        }
        if (isPawn(movingPiece) && !isValidPromotionChoice(fromIndex, toIndex, movePromoCode(move) != 0)) {
            return false;
        }

        boolean movingWhite = whiteToMove;
        char promotion = promoCharFromCode(movePromoCode(move));
        UndoInfo undo = makeMove(fromIndex, toIndex, promotion, false);
        boolean legal = !isKingInCheck(movingWhite);
        unmakeMove(undo);
        return legal;
    }

    private String resolveMoveInput(String move, boolean verbose) {
        if (move == null) {
            return failResolvedMove(verbose, "Move cannot be empty.");
        }

        String trimmedMove = move.trim();
        if (trimmedMove.isEmpty()) {
            return failResolvedMove(verbose, "Move cannot be empty.");
        }

        String normalizedMove = trimmedMove.toLowerCase();
        if (looksLikeUciMove(normalizedMove)) {
            return normalizedMove;
        }

        String normalizedSan = normalizeSan(trimmedMove);
        List<String> legalMoves = legalMovesAsStrings();
        List<String> matches = new ArrayList<>();

        for (String legalMove : legalMoves) {
            String san = toSan(legalMove, legalMoves);
            if (normalizeSan(san).equals(normalizedSan)) {
                matches.add(legalMove);
            }
        }

        if (matches.isEmpty()) {
            return failResolvedMove(verbose, "Not a legal move in algebraic or coordinate notation.");
        }

        if (matches.size() > 1) {
            return failResolvedMove(verbose, "Move is ambiguous. Add file or rank disambiguation.");
        }

        return matches.get(0);
    }

    private String failResolvedMove(boolean verbose, String message) {
        if (verbose) {
            System.out.println(message);
        }
        return null;
    }

    private boolean looksLikeUciMove(String move) {
        if (move.length() < 4 || move.length() > 5) {
            return false;
        }

        if (!isValidFile(move.charAt(0)) || !isValidRank(move.charAt(1))
                || !isValidFile(move.charAt(2)) || !isValidRank(move.charAt(3))) {
            return false;
        }

        return move.length() != 5 || isValidPromotionPiece(move.charAt(4));
    }

    private String normalizeSan(String move) {
        String normalized = move.trim()
                .replace('0', 'O')
                .replaceAll("[+=#!?]", "")
                .replaceAll("\\s+", "");
        return normalized.toLowerCase();
    }

    private void fillSquares(char[] squares, long bitboard, char piece) {
        while (bitboard != 0) {
            int square = Long.numberOfTrailingZeros(bitboard);
            squares[square] = piece;
            bitboard &= bitboard - 1;
        }
    }

    private boolean isValidFile(char file) {
        return file >= 'a' && file <= 'h';
    }

    private boolean isValidRank(char rank) {
        return rank >= '1' && rank <= '8';
    }

    private boolean isValidPromotionPiece(char piece) {
        return piece == 'q' || piece == 'n' || piece == 'r' || piece == 'b';
    }

    private String toSan(String move, List<String> legalMoves) {
        int fromIndex = toSquareIndex(move.substring(0, 2));
        int toIndex = toSquareIndex(move.substring(2, 4));
        char movingPiece = getPieceAt(fromIndex);
        char capturedPiece = getPieceAt(toIndex);
        boolean capture = capturedPiece != '.'
                || isEnPassantCapture(movingPiece, fromIndex, toIndex, capturedPiece);

        if (isCastlingMove(movingPiece, fromIndex, toIndex)) {
            return appendCheckSuffix(move, toIndex == 6 || toIndex == 62 ? "O-O" : "O-O-O");
        }

        StringBuilder san = new StringBuilder();
        if (!isPawn(movingPiece)) {
            san.append(Character.toUpperCase(movingPiece));
            appendSanDisambiguation(san, move, legalMoves, movingPiece, toIndex);
        } else if (capture) {
            san.append((char) ('a' + (fromIndex % 8)));
        }

        if (capture) {
            san.append('x');
        }

        san.append(toSquareName(toIndex));

        if (move.length() == 5) {
            san.append('=').append(Character.toUpperCase(move.charAt(4)));
        }

        return appendCheckSuffix(move, san.toString());
    }

    private void appendSanDisambiguation(StringBuilder san, String move, List<String> legalMoves, char movingPiece, int toIndex) {
        int fromIndex = toSquareIndex(move.substring(0, 2));
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        boolean sameFileConflict = false;
        boolean sameRankConflict = false;
        boolean conflictExists = false;

        for (String otherMove : legalMoves) {
            if (otherMove.equals(move)) {
                continue;
            }

            int otherFromIndex = toSquareIndex(otherMove.substring(0, 2));
            int otherToIndex = toSquareIndex(otherMove.substring(2, 4));
            if (otherToIndex != toIndex || getPieceAt(otherFromIndex) != movingPiece) {
                continue;
            }

            conflictExists = true;
            if (otherFromIndex % 8 == fromFile) {
                sameFileConflict = true;
            }
            if (otherFromIndex / 8 == fromRank) {
                sameRankConflict = true;
            }
        }

        if (!conflictExists) {
            return;
        }

        if (!sameFileConflict) {
            san.append((char) ('a' + fromFile));
            return;
        }

        if (!sameRankConflict) {
            san.append((char) ('1' + fromRank));
            return;
        }

        san.append((char) ('a' + fromFile));
        san.append((char) ('1' + fromRank));
    }

    private String appendCheckSuffix(String move, String san) {
        UndoInfo undo = makeMove(move);
        boolean checkmate;
        boolean inCheck;
        try {
            checkmate = isCheckmate();
            inCheck = isCurrentPlayerInCheck();
        } finally {
            unmakeMove(undo);
        }

        if (checkmate) {
            return san + "#";
        }

        if (inCheck) {
            return san + "+";
        }

        return san;
    }

    private boolean isValidFenPiece(char piece) {
        return "PNBRQKpnbrqk".indexOf(piece) >= 0;
    }

    private boolean failMove(boolean verbose, String message) {
        if (verbose) {
            System.out.println(message);
        }
        return false;
    }

    private boolean isValidCastlingRights(String rights) {
        if (rights.equals("-")) {
            return true;
        }

        if (rights.length() < 1 || rights.length() > 4) {
            return false;
        }

        String validRights = "KQkq";
        for (int i = 0; i < rights.length(); i++) {
            char right = rights.charAt(i);
            if (validRights.indexOf(right) == -1 || rights.indexOf(right) != i) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidEnPassantSquare(String square) {
        if (square.equals("-")) {
            return true;
        }

        if (square.length() != 2) {
            return false;
        }

        char file = square.charAt(0);
        char rank = square.charAt(1);
        return isValidFile(file) && (rank == '3' || rank == '6');
    }

    private boolean isNonNegativeInteger(String value) {
        try {
            return Integer.parseInt(value) >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isPositiveInteger(String value) {
        try {
            return Integer.parseInt(value) > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private boolean isPawn(char piece) {
        return piece == 'P' || piece == 'p';
    }

    private boolean isRook(char piece) {
        return piece == 'R' || piece == 'r';
    }

    private boolean isBishop(char piece) {
        return piece == 'B' || piece == 'b';
    }

    private boolean isQueen(char piece) {
        return piece == 'Q' || piece == 'q';
    }

    private boolean isKnight(char piece) {
        return piece == 'N' || piece == 'n';
    }

    private boolean isKing(char piece) {
        return piece == 'K' || piece == 'k';
    }

    private boolean hasOwnPiece(String square) {
        int squareIndex = toSquareIndex(square);
        long squareMask = 1L << squareIndex;
        long ownPieces = whiteToMove ? getWhitePieces() : getBlackPieces();
        return (ownPieces & squareMask) != 0;
    }

    private char getPieceAt(int squareIndex) {
        long squareMask = 1L << squareIndex;

        if ((whitePawns & squareMask) != 0) return 'P';
        if ((whiteKnights & squareMask) != 0) return 'N';
        if ((whiteBishops & squareMask) != 0) return 'B';
        if ((whiteRooks & squareMask) != 0) return 'R';
        if ((whiteQueen & squareMask) != 0) return 'Q';
        if ((whiteKing & squareMask) != 0) return 'K';
        if ((blackPawns & squareMask) != 0) return 'p';
        if ((blackKnights & squareMask) != 0) return 'n';
        if ((blackBishops & squareMask) != 0) return 'b';
        if ((blackRooks & squareMask) != 0) return 'r';
        if ((blackQueen & squareMask) != 0) return 'q';
        if ((blackKing & squareMask) != 0) return 'k';

        return '.';
    }

    private void clearSquare(long squareMask) {
        whitePawns &= ~squareMask;
        whiteKnights &= ~squareMask;
        whiteBishops &= ~squareMask;
        whiteRooks &= ~squareMask;
        whiteQueen &= ~squareMask;
        whiteKing &= ~squareMask;
        blackPawns &= ~squareMask;
        blackKnights &= ~squareMask;
        blackBishops &= ~squareMask;
        blackRooks &= ~squareMask;
        blackQueen &= ~squareMask;
        blackKing &= ~squareMask;
    }

    private void movePieceOnBitboards(char piece, long fromMask, long toMask) {
        switch (piece) {
            case 'P' -> whitePawns = (whitePawns & ~fromMask) | toMask;
            case 'N' -> whiteKnights = (whiteKnights & ~fromMask) | toMask;
            case 'B' -> whiteBishops = (whiteBishops & ~fromMask) | toMask;
            case 'R' -> whiteRooks = (whiteRooks & ~fromMask) | toMask;
            case 'Q' -> whiteQueen = (whiteQueen & ~fromMask) | toMask;
            case 'K' -> whiteKing = (whiteKing & ~fromMask) | toMask;
            case 'p' -> blackPawns = (blackPawns & ~fromMask) | toMask;
            case 'n' -> blackKnights = (blackKnights & ~fromMask) | toMask;
            case 'b' -> blackBishops = (blackBishops & ~fromMask) | toMask;
            case 'r' -> blackRooks = (blackRooks & ~fromMask) | toMask;
            case 'q' -> blackQueen = (blackQueen & ~fromMask) | toMask;
            case 'k' -> blackKing = (blackKing & ~fromMask) | toMask;
            default -> throw new IllegalStateException("No piece on origin square");
        }
    }

    private void promotePiece(char promotionPiece, long squareMask) {
        if (whiteToMove) {
            whitePawns &= ~squareMask;
            switch (promotionPiece) {
                case 'q' -> whiteQueen |= squareMask;
                case 'n' -> whiteKnights |= squareMask;
                case 'r' -> whiteRooks |= squareMask;
                case 'b' -> whiteBishops |= squareMask;
                default -> throw new IllegalArgumentException("Invalid promotion piece");
            }
            return;
        }

        blackPawns &= ~squareMask;
        switch (promotionPiece) {
            case 'q' -> blackQueen |= squareMask;
            case 'n' -> blackKnights |= squareMask;
            case 'r' -> blackRooks |= squareMask;
            case 'b' -> blackBishops |= squareMask;
            default -> throw new IllegalArgumentException("Invalid promotion piece");
        }
    }

    private boolean isCastlingMove(char movingPiece, int fromIndex, int toIndex) {
        return (movingPiece == 'K' || movingPiece == 'k') && Math.abs(toIndex - fromIndex) == 2;
    }

    private void moveRookForCastling(int kingToIndex) {
        switch (kingToIndex) {
            case 6 -> whiteRooks = (whiteRooks & ~(1L << 7)) | (1L << 5);
            case 2 -> whiteRooks = (whiteRooks & ~(1L << 0)) | (1L << 3);
            case 62 -> blackRooks = (blackRooks & ~(1L << 63)) | (1L << 61);
            case 58 -> blackRooks = (blackRooks & ~(1L << 56)) | (1L << 59);
            default -> throw new IllegalStateException("Invalid castling target square");
        }
    }

    private boolean isLegalPawnMove(int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        int toFile = toIndex % 8;
        int toRank = toIndex / 8;
        int fileDelta = toFile - fromFile;
        int rankDelta = toRank - fromRank;
        boolean movingWhite = whiteToMove;
        int forwardStep = movingWhite ? 1 : -1;
        int startRank = movingWhite ? 1 : 6;
        long toMask = 1L << toIndex;

        if (fileDelta == 0) {
            if (rankDelta == forwardStep) {
                return !isOccupied(toMask);
            }

            if (rankDelta == 2 * forwardStep && fromRank == startRank) {
                int intermediateIndex = fromIndex + (8 * forwardStep);
                long intermediateMask = 1L << intermediateIndex;
                return !isOccupied(intermediateMask) && !isOccupied(toMask);
            }

            return false;
        }

        if (Math.abs(fileDelta) == 1 && rankDelta == forwardStep) {
            return hasOpponentPiece(toMask) || isEnPassantTargetSquare(toIndex);
        }

        return false;
    }

    private boolean isLegalRookMove(int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        int toFile = toIndex % 8;
        int toRank = toIndex / 8;

        if (fromFile != toFile && fromRank != toRank) {
            return false;
        }

        if (fromIndex == toIndex) {
            return false;
        }

        return isPathClear(fromIndex, toIndex);
    }

    private boolean isLegalBishopMove(int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        int toFile = toIndex % 8;
        int toRank = toIndex / 8;

        if (fromIndex == toIndex) {
            return false;
        }

        if (Math.abs(toFile - fromFile) != Math.abs(toRank - fromRank)) {
            return false;
        }

        return isPathClear(fromIndex, toIndex);
    }

    private boolean isLegalQueenMove(int fromIndex, int toIndex) {
        return isLegalRookMove(fromIndex, toIndex) || isLegalBishopMove(fromIndex, toIndex);
    }

    private boolean isLegalKnightMove(int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        int toFile = toIndex % 8;
        int toRank = toIndex / 8;

        int fileDelta = Math.abs(toFile - fromFile);
        int rankDelta = Math.abs(toRank - fromRank);

        return (fileDelta == 1 && rankDelta == 2) || (fileDelta == 2 && rankDelta == 1);
    }

    private boolean isLegalKingMove(int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        int toFile = toIndex % 8;
        int toRank = toIndex / 8;
        int fileDelta = Math.abs(toFile - fromFile);
        int rankDelta = Math.abs(toRank - fromRank);

        if (fromIndex == toIndex) {
            return false;
        }

        if (fileDelta <= 1 && rankDelta <= 1) {
            return !isSquareAttackedAfterKingMove(fromIndex, toIndex, whiteToMove);
        }

        if (rankDelta == 0 && fileDelta == 2) {
            return isLegalCastlingMove(fromIndex, toIndex);
        }

        return false;
    }

    private boolean isValidPromotionChoice(int fromIndex, int toIndex, boolean hasPromotionPiece) {
        char movingPiece = getPieceAt(fromIndex);
        if (!isPawn(movingPiece)) {
            return !hasPromotionPiece;
        }

        int targetRank = toIndex / 8;
        boolean promotionRank = (movingPiece == 'P' && targetRank == 7)
                || (movingPiece == 'p' && targetRank == 0);
        return promotionRank == hasPromotionPiece;
    }

    private int toSquareIndex(String square) {
        int file = square.charAt(0) - 'a';
        int rank = square.charAt(1) - '1';
        return rank * 8 + file;
    }

    private long getWhitePieces() {
        return whitePawns | whiteKnights | whiteBishops | whiteRooks | whiteQueen | whiteKing;
    }

    private long getBlackPieces() {
        return blackPawns | blackKnights | blackBishops | blackRooks | blackQueen | blackKing;
    }

    private boolean isOccupied(long squareMask) {
        return ((getWhitePieces() | getBlackPieces()) & squareMask) != 0;
    }

    private boolean hasOpponentPiece(long squareMask) {
        long opponentPieces = whiteToMove ? getBlackPieces() : getWhitePieces();
        return (opponentPieces & squareMask) != 0;
    }

    private boolean isEnPassantTargetSquare(int toIndex) {
        return !enPassantTarget.equals("-") && toSquareName(toIndex).equals(enPassantTarget);
    }

    private boolean isEnPassantCapture(char movingPiece, int fromIndex, int toIndex, char capturedPiece) {
        if (!isPawn(movingPiece) || capturedPiece != '.') {
            return false;
        }

        int fromFile = fromIndex % 8;
        int toFile = toIndex % 8;
        int fromRank = fromIndex / 8;
        int toRank = toIndex / 8;
        int forwardStep = Character.isUpperCase(movingPiece) ? 1 : -1;

        return Math.abs(toFile - fromFile) == 1
                && toRank - fromRank == forwardStep
                && isEnPassantTargetSquare(toIndex);
    }

    private boolean isLegalCastlingMove(int fromIndex, int toIndex) {
        if (whiteToMove) {
            if (fromIndex != 4) {
                return false;
            }

            if (toIndex == 6) {
                return canCastle(true, true);
            }

            if (toIndex == 2) {
                return canCastle(true, false);
            }

            return false;
        }

        if (fromIndex != 60) {
            return false;
        }

        if (toIndex == 62) {
            return canCastle(false, true);
        }

        if (toIndex == 58) {
            return canCastle(false, false);
        }

        return false;
    }

    private boolean canCastle(boolean white, boolean kingSide) {
        char requiredRight = white
                ? (kingSide ? 'K' : 'Q')
                : (kingSide ? 'k' : 'q');
        if (castlingRights.indexOf(requiredRight) == -1) {
            return false;
        }

        int kingFrom = white ? 4 : 60;
        int rookFrom = white
                ? (kingSide ? 7 : 0)
                : (kingSide ? 63 : 56);
        int[] emptySquares = white
                ? (kingSide ? new int[]{5, 6} : new int[]{1, 2, 3})
                : (kingSide ? new int[]{61, 62} : new int[]{57, 58, 59});
        int[] kingPath = white
                ? (kingSide ? new int[]{4, 5, 6} : new int[]{4, 3, 2})
                : (kingSide ? new int[]{60, 61, 62} : new int[]{60, 59, 58});
        char expectedKing = white ? 'K' : 'k';
        char expectedRook = white ? 'R' : 'r';

        if (getPieceAt(kingFrom) != expectedKing || getPieceAt(rookFrom) != expectedRook) {
            return false;
        }

        for (int square : emptySquares) {
            if (isOccupied(1L << square)) {
                return false;
            }
        }

        for (int square : kingPath) {
            if (isSquareAttacked(square, !white)) {
                return false;
            }
        }

        return true;
    }

    private boolean isSquareAttackedAfterKingMove(int fromIndex, int toIndex, boolean whiteKingMove) {
        long fromMask = 1L << fromIndex;
        long toMask = 1L << toIndex;
        char capturedPiece = getPieceAt(toIndex);

        if (whiteKingMove) {
            whiteKing = (whiteKing & ~fromMask) | toMask;
        } else {
            blackKing = (blackKing & ~fromMask) | toMask;
        }
        clearSquare(toMask);
        if (whiteKingMove) {
            whiteKing |= toMask;
        } else {
            blackKing |= toMask;
        }

        boolean attacked = isSquareAttacked(toIndex, !whiteKingMove);

        if (whiteKingMove) {
            whiteKing = (whiteKing & ~toMask) | fromMask;
        } else {
            blackKing = (blackKing & ~toMask) | fromMask;
        }
        restoreCapturedPiece(capturedPiece, toMask);

        return attacked;
    }

    private void restoreCapturedPiece(char piece, long squareMask) {
        switch (piece) {
            case 'P' -> whitePawns |= squareMask;
            case 'N' -> whiteKnights |= squareMask;
            case 'B' -> whiteBishops |= squareMask;
            case 'R' -> whiteRooks |= squareMask;
            case 'Q' -> whiteQueen |= squareMask;
            case 'K' -> whiteKing |= squareMask;
            case 'p' -> blackPawns |= squareMask;
            case 'n' -> blackKnights |= squareMask;
            case 'b' -> blackBishops |= squareMask;
            case 'r' -> blackRooks |= squareMask;
            case 'q' -> blackQueen |= squareMask;
            case 'k' -> blackKing |= squareMask;
            case '.' -> {
            }
            default -> throw new IllegalStateException("Unknown piece to restore");
        }
    }

    private boolean isSquareAttacked(int squareIndex, boolean attackedByWhite) {
        return isAttackedByPawn(squareIndex, attackedByWhite)
                || isAttackedByKnight(squareIndex, attackedByWhite)
                || isAttackedByKing(squareIndex, attackedByWhite)
                || isAttackedAlongDirections(squareIndex, attackedByWhite, true)
                || isAttackedAlongDirections(squareIndex, attackedByWhite, false);
    }

    private boolean isKingInCheck(boolean whiteKing) {
        long kingBoard = whiteKing ? whiteKing() : blackKing();
        if (kingBoard == 0) {
            return false;
        }

        int kingSquare = Long.numberOfTrailingZeros(kingBoard);
        return isSquareAttacked(kingSquare, !whiteKing);
    }

    private long whiteKing() {
        return whiteKing;
    }

    private long blackKing() {
        return blackKing;
    }

    private boolean isAttackedByPawn(int squareIndex, boolean attackedByWhite) {
        int rank = squareIndex / 8;
        int file = squareIndex % 8;
        int pawnRank = attackedByWhite ? rank - 1 : rank + 1;

        if (pawnRank < 0 || pawnRank > 7) {
            return false;
        }

        long pawns = attackedByWhite ? whitePawns : blackPawns;

        if (file > 0) {
            int pawnIndex = pawnRank * 8 + (file - 1);
            if ((pawns & (1L << pawnIndex)) != 0) {
                return true;
            }
        }

        if (file < 7) {
            int pawnIndex = pawnRank * 8 + (file + 1);
            if ((pawns & (1L << pawnIndex)) != 0) {
                return true;
            }
        }

        return false;
    }

    private boolean isAttackedByKnight(int squareIndex, boolean attackedByWhite) {
        int rank = squareIndex / 8;
        int file = squareIndex % 8;
        long knights = attackedByWhite ? whiteKnights : blackKnights;
        int[][] offsets = {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                {1, -2}, {1, 2}, {2, -1}, {2, 1}
        };

        for (int[] offset : offsets) {
            int targetRank = rank + offset[0];
            int targetFile = file + offset[1];
            if (targetRank < 0 || targetRank > 7 || targetFile < 0 || targetFile > 7) {
                continue;
            }

            int targetIndex = targetRank * 8 + targetFile;
            if ((knights & (1L << targetIndex)) != 0) {
                return true;
            }
        }

        return false;
    }

    private boolean isAttackedByKing(int squareIndex, boolean attackedByWhite) {
        int rank = squareIndex / 8;
        int file = squareIndex % 8;
        long king = attackedByWhite ? whiteKing : blackKing;

        for (int rankOffset = -1; rankOffset <= 1; rankOffset++) {
            for (int fileOffset = -1; fileOffset <= 1; fileOffset++) {
                if (rankOffset == 0 && fileOffset == 0) {
                    continue;
                }

                int targetRank = rank + rankOffset;
                int targetFile = file + fileOffset;
                if (targetRank < 0 || targetRank > 7 || targetFile < 0 || targetFile > 7) {
                    continue;
                }

                int targetIndex = targetRank * 8 + targetFile;
                if ((king & (1L << targetIndex)) != 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isAttackedAlongDirections(int squareIndex, boolean attackedByWhite, boolean diagonal) {
        int[][] directions = diagonal
                ? new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}
                : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        char bishop = attackedByWhite ? 'B' : 'b';
        char rook = attackedByWhite ? 'R' : 'r';
        char queen = attackedByWhite ? 'Q' : 'q';
        int startRank = squareIndex / 8;
        int startFile = squareIndex % 8;

        for (int[] direction : directions) {
            int rank = startRank + direction[0];
            int file = startFile + direction[1];

            while (rank >= 0 && rank <= 7 && file >= 0 && file <= 7) {
                int targetIndex = rank * 8 + file;
                char piece = getPieceAt(targetIndex);
                if (piece != '.') {
                    if (diagonal) {
                        if (piece == bishop || piece == queen) {
                            return true;
                        }
                        break;
                    }
                    if (piece == rook || piece == queen) {
                        return true;
                    }
                    break;
                }
                rank += direction[0];
                file += direction[1];
            }
        }

        return false;
    }

    private void updateCastlingRights(char movingPiece, int fromIndex, int toIndex, char capturedPiece) {
        if (movingPiece == 'K') {
            castlingRights = removeCastlingRight(castlingRights, 'K');
            castlingRights = removeCastlingRight(castlingRights, 'Q');
        } else if (movingPiece == 'k') {
            castlingRights = removeCastlingRight(castlingRights, 'k');
            castlingRights = removeCastlingRight(castlingRights, 'q');
        } else if (movingPiece == 'R') {
            if (fromIndex == 0) {
                castlingRights = removeCastlingRight(castlingRights, 'Q');
            } else if (fromIndex == 7) {
                castlingRights = removeCastlingRight(castlingRights, 'K');
            }
        } else if (movingPiece == 'r') {
            if (fromIndex == 56) {
                castlingRights = removeCastlingRight(castlingRights, 'q');
            } else if (fromIndex == 63) {
                castlingRights = removeCastlingRight(castlingRights, 'k');
            }
        }

        if (capturedPiece == 'R') {
            if (toIndex == 0) {
                castlingRights = removeCastlingRight(castlingRights, 'Q');
            } else if (toIndex == 7) {
                castlingRights = removeCastlingRight(castlingRights, 'K');
            }
        } else if (capturedPiece == 'r') {
            if (toIndex == 56) {
                castlingRights = removeCastlingRight(castlingRights, 'q');
            } else if (toIndex == 63) {
                castlingRights = removeCastlingRight(castlingRights, 'k');
            }
        }
    }

    private void updateEnPassantTarget(char movingPiece, int fromIndex, int toIndex) {
        enPassantTarget = "-";

        if (!isPawn(movingPiece)) {
            return;
        }

        int fromRank = fromIndex / 8;
        int toRank = toIndex / 8;
        if (Math.abs(toRank - fromRank) != 2) {
            return;
        }

        int intermediateIndex = (fromIndex + toIndex) / 2;
        enPassantTarget = toSquareName(intermediateIndex);
    }

    private void updateMoveCounters(char movingPiece, char capturedPiece) {
        if (isPawn(movingPiece) || capturedPiece != '.') {
            halfmoveClock = 0;
        } else {
            halfmoveClock++;
        }

        if (!whiteToMove) {
            fullmoveNumber++;
        }
    }

    private String removeCastlingRight(String rights, char rightToRemove) {
        String updatedRights = rights.replace(String.valueOf(rightToRemove), "");
        return updatedRights.isEmpty() ? "-" : updatedRights;
    }

    private String toSquareName(int squareIndex) {
        int file = squareIndex % 8;
        int rank = squareIndex / 8;
        return String.valueOf((char) ('a' + file)) + (char) ('1' + rank);
    }

    // ---- Packed integer move encoding ----
    // A move is packed into an int: from(bits 0-5) | to(bits 6-11) | promo(bits 12-14).
    // The promo code is 0=none, 1=q, 2=r, 3=b, 4=n. NO_MOVE (0) is an impossible
    // a1a1 move, used as a null sentinel. This is the internal representation used
    // throughout move generation and search; Strings are produced only at the API
    // boundary. Castling and en passant need no flag — they are recoverable from
    // the from/to squares plus the moving piece, exactly as in the String form.
    private static final int NO_MOVE = 0;

    private static int packMove(int from, int to, int promoCode) {
        return (from & 0x3F) | ((to & 0x3F) << 6) | ((promoCode & 0x7) << 12);
    }

    private static int moveFromSq(int move) {
        return move & 0x3F;
    }

    private static int moveToSq(int move) {
        return (move >> 6) & 0x3F;
    }

    private static int movePromoCode(int move) {
        return (move >> 12) & 0x7;
    }

    private static char promoCharFromCode(int code) {
        return switch (code) {
            case 1 -> 'q';
            case 2 -> 'r';
            case 3 -> 'b';
            case 4 -> 'n';
            default -> NO_PROMOTION;
        };
    }

    private static int promoCodeFromChar(char c) {
        return switch (c) {
            case 'q' -> 1;
            case 'r' -> 2;
            case 'b' -> 3;
            case 'n' -> 4;
            default -> 0;
        };
    }

    private String moveToString(int move) {
        String base = toSquareName(moveFromSq(move)) + toSquareName(moveToSq(move));
        int promo = movePromoCode(move);
        return promo == 0 ? base : base + promoCharFromCode(promo);
    }

    private int stringToMove(String move) {
        int from = toSquareIndex(move.substring(0, 2));
        int to = toSquareIndex(move.substring(2, 4));
        int promo = move.length() == 5 ? promoCodeFromChar(move.charAt(4)) : 0;
        return packMove(from, to, promo);
    }


    private Board copy() {
        Board board = new Board();
        board.copyStateFrom(this);
        return board;
    }

    private void copyStateFrom(Board other) {
        whiteToMove = other.whiteToMove;
        castlingRights = other.castlingRights;
        enPassantTarget = other.enPassantTarget;
        halfmoveClock = other.halfmoveClock;
        fullmoveNumber = other.fullmoveNumber;
        whitePawns = other.whitePawns;
        whiteKnights = other.whiteKnights;
        whiteBishops = other.whiteBishops;
        whiteRooks = other.whiteRooks;
        whiteQueen = other.whiteQueen;
        whiteKing = other.whiteKing;
        blackPawns = other.blackPawns;
        blackKnights = other.blackKnights;
        blackBishops = other.blackBishops;
        blackRooks = other.blackRooks;
        blackQueen = other.blackQueen;
        blackKing = other.blackKing;
        positionHistory = new HashMap<>(other.positionHistory);
        positionZobristKey = computeZobristKey();
    }

    private void resetPositionHistory() {
        positionHistory = new HashMap<>();
        recordCurrentPosition();
    }

    private void resetSearchHeuristics() {
        ensureTranspositionTable();
        searchGeneration++;
        tablebaseHits = 0;
        for (int i = 0; i < historyHeuristic.length; i++) {
            Arrays.fill(historyHeuristic[i], 0);
            killerMoves1[i] = NO_MOVE;
            killerMoves2[i] = NO_MOVE;
        }
    }

    private void ensureTranspositionTable() {
        if (transpositionTable == null) {
            transpositionTable = new TranspositionEntry[TRANSPOSITION_TABLE_SIZE];
        }
    }

    private TranspositionEntry probeTransposition(long key) {
        if (transpositionTable == null) {
            return null;
        }
        TranspositionEntry entry = transpositionTable[(int) (key & TRANSPOSITION_TABLE_MASK)];
        return entry != null && entry.key == key ? entry : null;
    }

    private void storeTransposition(long key, int depth, int value, int flag, int bestMove) {
        ensureTranspositionTable();
        int index = (int) (key & TRANSPOSITION_TABLE_MASK);
        TranspositionEntry existing = transpositionTable[index];
        // Depth-preferred replacement with aging: always replace an empty slot or
        // an entry left over from a previous search, otherwise keep the entry that
        // was searched to greater depth.
        if (existing == null || existing.age != searchGeneration || depth >= existing.depth) {
            transpositionTable[index] = new TranspositionEntry(key, depth, value, flag, bestMove, searchGeneration);
        }
    }

    private int adjustMateScoreForStorage(int value, int ply) {
        if (value >= MATE_SCORE_THRESHOLD) {
            return value + ply;
        }
        if (value <= -MATE_SCORE_THRESHOLD) {
            return value - ply;
        }
        return value;
    }

    private int adjustMateScoreFromStorage(int value, int ply) {
        if (value >= MATE_SCORE_THRESHOLD) {
            return value - ply;
        }
        if (value <= -MATE_SCORE_THRESHOLD) {
            return value + ply;
        }
        return value;
    }

    private long computeZobristKey() {
        long key = 0L;
        key ^= hashPieces(whitePawns, 0);
        key ^= hashPieces(whiteKnights, 1);
        key ^= hashPieces(whiteBishops, 2);
        key ^= hashPieces(whiteRooks, 3);
        key ^= hashPieces(whiteQueen, 4);
        key ^= hashPieces(whiteKing, 5);
        key ^= hashPieces(blackPawns, 6);
        key ^= hashPieces(blackKnights, 7);
        key ^= hashPieces(blackBishops, 8);
        key ^= hashPieces(blackRooks, 9);
        key ^= hashPieces(blackQueen, 10);
        key ^= hashPieces(blackKing, 11);

        if (whiteToMove) {
            key ^= ZOBRIST_SIDE;
        }
        key ^= ZOBRIST_CASTLING[castlingRightsIndex()];

        String effectiveEnPassant = getEffectiveEnPassantTarget();
        if (!effectiveEnPassant.equals("-")) {
            key ^= ZOBRIST_EP_FILE[toSquareIndex(effectiveEnPassant) % 8];
        }
        return key;
    }

    // Maps a piece character to its index into ZOBRIST_PIECE, matching the
    // ordering used by computeZobristKey (white P,N,B,R,Q,K = 0..5; black = 6..11).
    private static int zobristPieceIndex(char piece) {
        return switch (piece) {
            case 'P' -> 0;  case 'N' -> 1;  case 'B' -> 2;
            case 'R' -> 3;  case 'Q' -> 4;  case 'K' -> 5;
            case 'p' -> 6;  case 'n' -> 7;  case 'b' -> 8;
            case 'r' -> 9;  case 'q' -> 10; case 'k' -> 11;
            default -> throw new IllegalStateException("not a piece: " + piece);
        };
    }

    // The en-passant component of the key for the current position: the file key
    // when an en-passant capture is actually available, else 0. Mirrors the
    // getEffectiveEnPassantTarget()-based logic in computeZobristKey.
    private long epFileKey() {
        String effective = getEffectiveEnPassantTarget();
        return effective.equals("-") ? 0L : ZOBRIST_EP_FILE[toSquareIndex(effective) % 8];
    }

    private long hashPieces(long bitboard, int pieceIndex) {
        long key = 0L;
        long remaining = bitboard;
        while (remaining != 0) {
            int square = Long.numberOfTrailingZeros(remaining);
            key ^= ZOBRIST_PIECE[pieceIndex][square];
            remaining &= remaining - 1;
        }
        return key;
    }

    private int castlingRightsIndex() {
        int index = 0;
        if (castlingRights.indexOf('K') >= 0) index |= 1;
        if (castlingRights.indexOf('Q') >= 0) index |= 2;
        if (castlingRights.indexOf('k') >= 0) index |= 4;
        if (castlingRights.indexOf('q') >= 0) index |= 8;
        return index;
    }

    private void recordCurrentPosition() {
        String positionKey = getPositionKey();
        positionHistory.put(positionKey, positionHistory.getOrDefault(positionKey, 0) + 1);
    }

    private String getPositionKey() {
        return getBoardLayoutKey()
                + " "
                + (whiteToMove ? "w" : "b")
                + " "
                + castlingRights
                + " "
                + getEffectiveEnPassantTarget();
    }

    private String getBoardLayoutKey() {
        StringBuilder layout = new StringBuilder();

        for (int rank = 7; rank >= 0; rank--) {
            int emptySquares = 0;

            for (int file = 0; file < 8; file++) {
                char piece = getPieceAt(rank * 8 + file);
                if (piece == '.') {
                    emptySquares++;
                    continue;
                }

                if (emptySquares > 0) {
                    layout.append(emptySquares);
                    emptySquares = 0;
                }
                layout.append(piece);
            }

            if (emptySquares > 0) {
                layout.append(emptySquares);
            }

            if (rank > 0) {
                layout.append('/');
            }
        }

        return layout.toString();
    }

    private String getEffectiveEnPassantTarget() {
        if (enPassantTarget.equals("-")) {
            return "-";
        }

        int targetIndex = toSquareIndex(enPassantTarget);
        int targetFile = targetIndex % 8;
        int targetRank = targetIndex / 8;
        int pawnRank = whiteToMove ? targetRank - 1 : targetRank + 1;
        char pawn = whiteToMove ? 'P' : 'p';

        if (pawnRank < 0 || pawnRank > 7) {
            return "-";
        }

        for (int fileOffset : new int[]{-1, 1}) {
            int sourceFile = targetFile + fileOffset;
            if (sourceFile < 0 || sourceFile > 7) {
                continue;
            }

            int sourceIndex = pawnRank * 8 + sourceFile;
            if (getPieceAt(sourceIndex) == pawn) {
                return enPassantTarget;
            }
        }

        return "-";
    }

    private boolean bishopsOnSameColor() {
        int whiteBishopSquare = Long.numberOfTrailingZeros(whiteBishops);
        int blackBishopSquare = Long.numberOfTrailingZeros(blackBishops);
        return isLightSquare(whiteBishopSquare) == isLightSquare(blackBishopSquare);
    }

    private boolean isLightSquare(int squareIndex) {
        int rank = squareIndex / 8;
        int file = squareIndex % 8;
        return (rank + file) % 2 != 0;
    }

    private final int[] pseudoMoveScratch = new int[256];

    // Fills out[] with pseudo-legal moves (correct piece movement, but may leave
    // the king in check). Returns the count. Emission order matches the historical
    // generator so move-ordering tie-breaks are unchanged.
    private int generatePseudoLegal(int[] out) {
        int n = 0;
        long ownPieces = whiteToMove ? getWhitePieces() : getBlackPieces();

        for (int fromIndex = 0; fromIndex < 64; fromIndex++) {
            long fromMask = 1L << fromIndex;
            if ((ownPieces & fromMask) == 0) {
                continue;
            }

            char piece = getPieceAt(fromIndex);
            switch (Character.toLowerCase(piece)) {
                case 'p' -> n = addPawnMoves(fromIndex, out, n);
                case 'n' -> n = addKnightMoves(fromIndex, out, n);
                case 'b' -> n = addSlidingMoves(fromIndex, out, n, true, false);
                case 'r' -> n = addSlidingMoves(fromIndex, out, n, false, true);
                case 'q' -> n = addSlidingMoves(fromIndex, out, n, true, true);
                case 'k' -> n = addKingMoves(fromIndex, out, n);
                default -> {
                }
            }
        }
        return n;
    }

    // ---- Inline legality: per-node pin and check information ----
    private int[] generateLegalMoves() {
        int[] out = pseudoMoveScratch;
        int n = generatePseudoLegal(out);
        computeLegalityMasks();

        int[] legal = new int[n];
        int m = 0;
        for (int i = 0; i < n; i++) {
            if (isLegalGeneratedMove(out[i])) {
                legal[m++] = out[i];
            }
        }
        return m == legal.length ? legal : Arrays.copyOf(legal, m);
    }

    // Recomputed once per generateLegalMoves call. legalityCheckMask is the set of
    // destination squares that resolve a check (capture the checker or block a
    // sliding checker); it is all-ones when not in check. legalityPinned marks
    // own pieces that are absolutely pinned, and legalityPinRay[s] is the set of
    // squares a pinned piece on s may move to (the king–pinner line, pinner
    // inclusive, king exclusive).
    private int legalityCheckerCount;
    private long legalityCheckMask;
    private long legalityPinned;
    private final long[] legalityPinRay = new long[64];

    private static final int[][] KNIGHT_OFFSETS = {
            {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2}, {1, -2}, {1, 2}, {2, -1}, {2, 1}
    };
    private static final int[][] DIAGONAL_DIRS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ORTHOGONAL_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private void computeLegalityMasks() {
        boolean white = whiteToMove;
        long kingBoard = white ? whiteKing : blackKing;
        int ks = Long.numberOfTrailingZeros(kingBoard);
        int kr = ks / 8;
        int kf = ks % 8;

        long checkMask = 0L;
        int checkers = 0;
        long pinned = 0L;

        // Knight checks.
        long enemyKnights = white ? blackKnights : whiteKnights;
        for (int[] o : KNIGHT_OFFSETS) {
            int r = kr + o[0];
            int f = kf + o[1];
            if (r < 0 || r > 7 || f < 0 || f > 7) {
                continue;
            }
            int sq = r * 8 + f;
            if ((enemyKnights & (1L << sq)) != 0) {
                checkers++;
                checkMask |= 1L << sq;
            }
        }

        // Pawn checks: an enemy pawn positioned to capture the king square.
        long enemyPawns = white ? blackPawns : whitePawns;
        int pawnRank = white ? kr + 1 : kr - 1;
        if (pawnRank >= 0 && pawnRank <= 7) {
            if (kf > 0) {
                int sq = pawnRank * 8 + (kf - 1);
                if ((enemyPawns & (1L << sq)) != 0) {
                    checkers++;
                    checkMask |= 1L << sq;
                }
            }
            if (kf < 7) {
                int sq = pawnRank * 8 + (kf + 1);
                if ((enemyPawns & (1L << sq)) != 0) {
                    checkers++;
                    checkMask |= 1L << sq;
                }
            }
        }

        // Sliding directions: detect slider checks and absolute pins together.
        long ownPieces = white ? getWhitePieces() : getBlackPieces();
        char enemyBishop = white ? 'b' : 'B';
        char enemyRook = white ? 'r' : 'R';
        char enemyQueen = white ? 'q' : 'Q';

        for (int diag = 0; diag < 2; diag++) {
            int[][] dirs = diag == 0 ? DIAGONAL_DIRS : ORTHOGONAL_DIRS;
            for (int[] d : dirs) {
                long segBits = 0L;
                int firstOwn = -1;
                int r = kr + d[0];
                int f = kf + d[1];
                while (r >= 0 && r <= 7 && f >= 0 && f <= 7) {
                    int sq = r * 8 + f;
                    long bit = 1L << sq;
                    char p = getPieceAt(sq);
                    if (p == '.') {
                        segBits |= bit;
                        r += d[0];
                        f += d[1];
                        continue;
                    }
                    boolean enemySlider = diag == 0
                            ? (p == enemyBishop || p == enemyQueen)
                            : (p == enemyRook || p == enemyQueen);
                    if (firstOwn == -1) {
                        if (enemySlider) {
                            checkers++;
                            checkMask |= segBits | bit;   // block squares + capture square
                            break;
                        } else if ((ownPieces & bit) != 0) {
                            firstOwn = sq;
                            segBits |= bit;
                            r += d[0];
                            f += d[1];
                            continue;
                        } else {
                            break;                        // enemy non-slider blocks the ray
                        }
                    } else {
                        if (enemySlider) {
                            pinned |= 1L << firstOwn;
                            legalityPinRay[firstOwn] = segBits | bit;
                        }
                        break;                            // second piece resolves pin question
                    }
                }
            }
        }

        legalityCheckerCount = checkers;
        legalityCheckMask = checkers == 0 ? ~0L : checkMask;
        legalityPinned = pinned;
    }

    // Tests whether a pseudo-legal generated move is fully legal, using the
    // precomputed pin/check masks instead of make/unmake. King moves reuse the
    // existing king-removed attack test and castling legality; en passant (whose
    // rare discovered-check cases pins do not capture) falls back to make/unmake.
    private boolean isLegalGeneratedMove(int move) {
        int from = moveFromSq(move);
        int to = moveToSq(move);
        char piece = getPieceAt(from);

        if (isKing(piece)) {
            if (Math.abs((to % 8) - (from % 8)) == 2) {
                return isLegalCastlingMove(from, to);
            }
            return !isSquareAttackedAfterKingMove(from, to, whiteToMove);
        }

        if (isPawn(piece) && getPieceAt(to) == '.' && isEnPassantCapture(piece, from, to, '.')) {
            boolean movingWhite = whiteToMove;
            UndoInfo undo = makeMove(from, to, NO_PROMOTION, false);
            boolean legal = !isKingInCheck(movingWhite);
            unmakeMove(undo);
            return legal;
        }

        if (legalityCheckerCount >= 2) {
            return false;   // double check: only the king may move
        }
        long toBit = 1L << to;
        if ((toBit & legalityCheckMask) == 0) {
            return false;   // must capture the checker or block (all-ones if not in check)
        }
        if ((legalityPinned & (1L << from)) != 0 && (toBit & legalityPinRay[from]) == 0) {
            return false;   // pinned piece must stay on the king–pinner line
        }
        return true;
    }

    /**
     * Validation harness: walks the move tree to {@code depth} and, at every node,
     * asserts the inline legality filter ({@link #isLegalGeneratedMove}) agrees
     * move-for-move with the original make/unmake filter ({@link #moveIsFullyLegal})
     * over all pseudo-legal moves. Returns the perft count. Throws on the first
     * disagreement. This is the correctness anchor for the inline generator.
     */
    public long verifyLegalityPerft(int depth) {
        int[] pseudo = new int[256];
        int n = generatePseudoLegal(pseudo);
        computeLegalityMasks();
        for (int i = 0; i < n; i++) {
            boolean inlineLegal = isLegalGeneratedMove(pseudo[i]);
            boolean referenceLegal = moveIsFullyLegal(pseudo[i]);
            if (inlineLegal != referenceLegal) {
                throw new IllegalStateException("legality mismatch on " + moveToString(pseudo[i])
                        + " inline=" + inlineLegal + " reference=" + referenceLegal + " fen=" + toFen());
            }
        }
        if (depth == 0) {
            return 1L;
        }
        long total = 0L;
        for (int move : generateLegalMoves()) {
            UndoInfo undo = makeMove(move);
            total += verifyLegalityPerft(depth - 1);
            unmakeMove(undo);
        }
        return total;
    }

    // Low-level emitters: write a packed move into out[n], return the new count.
    private static int emitMove(int[] out, int n, int from, int to) {
        out[n] = packMove(from, to, 0);
        return n + 1;
    }

    // Emits the four promotion choices in q,r,b,n order to match the historical
    // String generator (so stable move ordering breaks ties identically).
    private static int emitPromotions(int[] out, int n, int from, int to) {
        out[n] = packMove(from, to, 1);
        out[n + 1] = packMove(from, to, 2);
        out[n + 2] = packMove(from, to, 3);
        out[n + 3] = packMove(from, to, 4);
        return n + 4;
    }

    private int addPawnMoves(int fromIndex, int[] out, int n) {
        int file = fromIndex % 8;
        int rank = fromIndex / 8;
        boolean movingWhite = whiteToMove;
        int forwardStep = movingWhite ? 1 : -1;
        int oneForwardRank = rank + forwardStep;

        if (oneForwardRank >= 0 && oneForwardRank <= 7) {
            int oneForwardIndex = oneForwardRank * 8 + file;
            if (!isOccupied(1L << oneForwardIndex)) {
                n = addPawnMoveNotation(fromIndex, oneForwardIndex, out, n);

                int startRank = movingWhite ? 1 : 6;
                int twoForwardRank = rank + (2 * forwardStep);
                if (rank == startRank && twoForwardRank >= 0 && twoForwardRank <= 7) {
                    int twoForwardIndex = twoForwardRank * 8 + file;
                    if (!isOccupied(1L << twoForwardIndex)) {
                        n = emitMove(out, n, fromIndex, twoForwardIndex);
                    }
                }
            }
        }

        int captureRank = rank + forwardStep;
        if (captureRank < 0 || captureRank > 7) {
            return n;
        }

        for (int fileOffset : new int[]{-1, 1}) {
            int targetFile = file + fileOffset;
            if (targetFile < 0 || targetFile > 7) {
                continue;
            }

            int targetIndex = captureRank * 8 + targetFile;
            long targetMask = 1L << targetIndex;
            if (hasOpponentPiece(targetMask) || isEnPassantTargetSquare(targetIndex)) {
                n = addPawnMoveNotation(fromIndex, targetIndex, out, n);
            }
        }
        return n;
    }

    private int addPawnMoveNotation(int fromIndex, int toIndex, int[] out, int n) {
        int targetRank = toIndex / 8;
        if (targetRank == 0 || targetRank == 7) {
            return emitPromotions(out, n, fromIndex, toIndex);
        }
        return emitMove(out, n, fromIndex, toIndex);
    }

    private int addKnightMoves(int fromIndex, int[] out, int n) {
        int rank = fromIndex / 8;
        int file = fromIndex % 8;
        int[][] offsets = {
                {-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                {1, -2}, {1, 2}, {2, -1}, {2, 1}
        };

        for (int[] offset : offsets) {
            int targetRank = rank + offset[0];
            int targetFile = file + offset[1];
            if (targetRank < 0 || targetRank > 7 || targetFile < 0 || targetFile > 7) {
                continue;
            }
            n = addMoveIfNotOwnPiece(fromIndex, targetRank * 8 + targetFile, out, n);
        }
        return n;
    }

    private int addSlidingMoves(int fromIndex, int[] out, int n, boolean diagonal, boolean orthogonal) {
        if (diagonal) {
            n = addSlidingMovesForDirections(fromIndex, out, n, new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}});
        }
        if (orthogonal) {
            n = addSlidingMovesForDirections(fromIndex, out, n, new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}});
        }
        return n;
    }

    private int addSlidingMovesForDirections(int fromIndex, int[] out, int n, int[][] directions) {
        int rank = fromIndex / 8;
        int file = fromIndex % 8;

        for (int[] direction : directions) {
            int targetRank = rank + direction[0];
            int targetFile = file + direction[1];

            while (targetRank >= 0 && targetRank <= 7 && targetFile >= 0 && targetFile <= 7) {
                int targetIndex = targetRank * 8 + targetFile;
                long targetMask = 1L << targetIndex;
                if (hasOwnPiece(targetMask)) {
                    break;
                }

                n = emitMove(out, n, fromIndex, targetIndex);
                if (hasOpponentPiece(targetMask)) {
                    break;
                }

                targetRank += direction[0];
                targetFile += direction[1];
            }
        }
        return n;
    }

    private int addKingMoves(int fromIndex, int[] out, int n) {
        int rank = fromIndex / 8;
        int file = fromIndex % 8;

        for (int rankOffset = -1; rankOffset <= 1; rankOffset++) {
            for (int fileOffset = -1; fileOffset <= 1; fileOffset++) {
                if (rankOffset == 0 && fileOffset == 0) {
                    continue;
                }

                int targetRank = rank + rankOffset;
                int targetFile = file + fileOffset;
                if (targetRank < 0 || targetRank > 7 || targetFile < 0 || targetFile > 7) {
                    continue;
                }

                n = addMoveIfNotOwnPiece(fromIndex, targetRank * 8 + targetFile, out, n);
            }
        }

        if (whiteToMove && fromIndex == 4) {
            n = emitMove(out, n, 4, 6);   // e1g1
            n = emitMove(out, n, 4, 2);   // e1c1
        } else if (!whiteToMove && fromIndex == 60) {
            n = emitMove(out, n, 60, 62); // e8g8
            n = emitMove(out, n, 60, 58); // e8c8
        }
        return n;
    }

    private int addMoveIfNotOwnPiece(int fromIndex, int toIndex, int[] out, int n) {
        if (!hasOwnPiece(1L << toIndex)) {
            return emitMove(out, n, fromIndex, toIndex);
        }
        return n;
    }

    private int addMoveNotation(int fromIndex, int toIndex, int[] out, int n) {
        return emitMove(out, n, fromIndex, toIndex);
    }

    private boolean hasOwnPiece(long squareMask) {
        long ownPieces = whiteToMove ? getWhitePieces() : getBlackPieces();
        return (ownPieces & squareMask) != 0;
    }

    private boolean isPathClear(int fromIndex, int toIndex) {
        int fromFile = fromIndex % 8;
        int fromRank = fromIndex / 8;
        int toFile = toIndex % 8;
        int toRank = toIndex / 8;

        int fileStep = Integer.compare(toFile, fromFile);
        int rankStep = Integer.compare(toRank, fromRank);
        int currentFile = fromFile + fileStep;
        int currentRank = fromRank + rankStep;

        while (currentFile != toFile || currentRank != toRank) {
            int currentIndex = currentRank * 8 + currentFile;
            if (isOccupied(1L << currentIndex)) {
                return false;
            }
            currentFile += fileStep;
            currentRank += rankStep;
        }

        return true;
    }
}
