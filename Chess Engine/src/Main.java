import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final int OPENING_MOVE_LIMIT = 10;
    private static final int REPEATED_ENGINE_PIECE_PENALTY = 40;
    private static final int VARIETY_DEVELOPMENT_BONUS = 10;

    // Shared opening book, loaded from the "polyglot.book" system property or a
    // "book.bin" in the working directory; null (and ignored) when none is
    // configured, so play is unchanged without a book.
    private static final PolyglotBook OPENING_BOOK = PolyglotBook.openDefault();

    public static void main(String[] args) {
        // Optional: load Syzygy tablebases if a path is supplied via
        //   -DsyzygyPath="C:\\...\\syzygy"   (no-op if unavailable).
        String syzygyPath = System.getProperty("syzygyPath", "");
        if (!syzygyPath.isBlank()) {
            boolean ok = SyzygyTablebase.init(syzygyPath);
            System.out.println(ok
                    ? "Syzygy tablebases loaded (up to " + SyzygyTablebase.maxPieces() + " pieces)."
                    : "Syzygy tablebases not loaded (native library or files missing).");
        }
        Scanner scanner = new Scanner(System.in);

        while (true) {
            Board board = new Board();
            List<String> sanMoves = new ArrayList<>();
            boolean playerIsWhite = choosePlayerColor(scanner);
            String startingFen = "";
            int startingMoveNumber = 1;
            String lastEnginePieceSquare = null;
            List<String> developedEnginePieceSquares = new ArrayList<>();
            boolean engineHasMovedCenterPawn = false;
            boolean showThinking = false;

            System.out.print("Enter starting FEN string, or leave blank to skip: ");
            String fen = scanner.nextLine().trim();
            if (!fen.isEmpty()) {
                if (board.loadFromFen(fen)) {
                    startingFen = fen;
                    startingMoveNumber = getStartingMoveNumber(fen);
                } else {
                    System.out.println("Unknown FEN string. Using default starting position.");
                }
            }

            String result = "*";
            while (true) {
                List<String> legalMoves = board.getLegalMoves();
                String drawReason = null;
                if (board.isDrawByInsufficientMaterial()) {
                    drawReason = "Draw by insufficient material.";
                } else if (board.isDrawByThreefoldRepetition()) {
                    drawReason = "Draw by threefold repetition.";
                } else if (board.isDrawByFiftyMoveRule()) {
                    drawReason = "Draw by fifty-move rule.";
                }

                if (drawReason != null) {
                    if (board.whiteToMove) {
                        System.out.println("\n     White to Move:");
                    } else {
                        System.out.println("\n     Black to Move:");
                    }
                    board.printBoard(playerIsWhite);
                    System.out.println(drawReason);
                    result = "1/2-1/2";
                    break;
                }

                if (legalMoves.isEmpty()) {
                    if (board.whiteToMove) {
                        System.out.println("\n     White to Move:");
                    } else {
                        System.out.println("\n     Black to Move:");
                    }
                    board.printBoard(playerIsWhite);
                    if (board.isCurrentPlayerInCheck()) {
                        String winner = board.whiteToMove ? "Black" : "White";
                        System.out.println("Checkmate. " + winner + " wins.");
                        result = board.whiteToMove ? "0-1" : "1-0";
                    } else {
                        System.out.println("Stalemate.");
                        result = "1/2-1/2";
                    }
                    break;
                }

                boolean playerTurn = board.whiteToMove == playerIsWhite;
                if (board.whiteToMove) {
                    System.out.println("\n     White to Move:");
                } else {
                    System.out.println("\n     Black to Move:");
                }
                board.printBoard(playerIsWhite);

                if (!playerTurn) {
                    String engineMove;
                    String bookMove = OPENING_BOOK == null ? null : OPENING_BOOK.probe(board);
                    if (bookMove != null) {
                        // In book: play instantly, skipping the search (and its
                        // opening-variety heuristics, which the book supersedes).
                        engineMove = bookMove;
                        if (showThinking) {
                            System.out.println("Book move.");
                            System.out.println();
                        }
                    } else {
                        String discouragedSourceSquare = isOpeningMove(sanMoves, startingMoveNumber)
                                ? lastEnginePieceSquare
                                : null;
                        List<String> developedSourceSquares = isOpeningMove(sanMoves, startingMoveNumber)
                                && engineHasMovedCenterPawn
                                ? developedEnginePieceSquares
                                : null;
                        Board.SearchReport searchReport = board.getSearchReport(3, 2500, 64, discouragedSourceSquare,
                                REPEATED_ENGINE_PIECE_PENALTY, developedSourceSquares, VARIETY_DEVELOPMENT_BONUS);
                        if (showThinking) {
                            printSearchReport(board, searchReport);
                        }
                        engineMove = searchReport.getBestMove();
                    }
                    String san = board.toSan(engineMove);
                    boolean centralPawnMove = board.isCentralPawnMove(engineMove);
                    System.out.println("Engine plays: " + san);
                    sanMoves.add(san);
                    board.movePiece(engineMove);
                    if (centralPawnMove) {
                        engineHasMovedCenterPawn = true;
                    }
                    updateDevelopedEnginePieceSquares(developedEnginePieceSquares, engineMove);
                    lastEnginePieceSquare = engineMove.substring(2, 4);
                    System.out.println();
                    continue;
                }

                while (true) {
                    System.out.print("Make your move: ");
                    String move = scanner.nextLine().trim();
                    if (move.equalsIgnoreCase("/fen")) {
                        System.out.println(board.toFen());
                        System.out.println();
                        continue;
                    }

                    if (move.equalsIgnoreCase("/pgn")) {
                        System.out.println(buildPgn(sanMoves, result, startingFen, startingMoveNumber));
                        System.out.println();
                        continue;
                    }

                    if (move.equalsIgnoreCase("/showthinking")) {
                        showThinking = !showThinking;
                        System.out.println("Engine thinking display " + (showThinking ? "enabled." : "disabled."));
                        System.out.println();
                        continue;
                    }

                    if (move.equalsIgnoreCase("/evaluation") || move.equalsIgnoreCase("/eval")) {
                        int evaluation = board.getMaterialEvaluationCentipawns();
                        System.out.printf("%+d %n%n", evaluation);
                        continue;
                    }

                    if (move.equalsIgnoreCase("/getmove")) {
                        List<String> bestMoves = board.getBestMoves(3);
                        for (String bestMove : bestMoves) {
                            System.out.println(board.toSan(bestMove));
                        }
                        System.out.println();
                        continue;
                    }

                    if (move.toLowerCase().startsWith("/calculate")) {
                        String[] parts = move.split("\\s+");
                        if (parts.length != 2) {
                            System.out.println("Usage: /calculate [depth]");
                            System.out.println();
                            continue;
                        }

                        try {
                            int depth = Integer.parseInt(parts[1]);
                            if (depth < 0) {
                                System.out.println("Depth must be non-negative.");
                            } else {
                                long count = board.countBoardStates(depth);
                                System.out.println(count);
                            }
                        } catch (NumberFormatException ex) {
                            System.out.println("Depth must be an integer.");
                        }

                        System.out.println();
                        continue;
                    }

                    try {
                        String san = board.toSan(move);
                        if (board.movePiece(move)) {
                            sanMoves.add(san);
                            break;
                        }
                    } catch (IllegalArgumentException ex) {
                        board.movePiece(move);
                    }
                    System.out.println();
                }

                System.out.println();
            }

            System.out.println();
            if (!promptPlayAgain(scanner, sanMoves, result, startingFen, startingMoveNumber)) {
                break;
            }
        }
    }

    private static boolean isOpeningMove(List<String> sanMoves, int startingMoveNumber) {
        return startingMoveNumber + (sanMoves.size() / 2) <= OPENING_MOVE_LIMIT;
    }

    private static void updateDevelopedEnginePieceSquares(List<String> developedEnginePieceSquares, String engineMove) {
        developedEnginePieceSquares.remove(engineMove.substring(0, 2));
        developedEnginePieceSquares.add(engineMove.substring(2, 4));
    }

    private static void printSearchReport(Board board, Board.SearchReport searchReport) {
        for (int depth : searchReport.getCompletedDepths()) {
            System.out.println("Finished Depth " + depth + " Search");
        }
        if (searchReport.timedOut()) {
            System.out.println("Timeout.");
        }

        System.out.println();
        System.out.println("Top Moves:");
        List<Board.MoveEvaluation> topMoves = searchReport.getTopMoves(3);
        for (int i = 0; i < topMoves.size(); i++) {
            Board.MoveEvaluation moveEvaluation = topMoves.get(i);
            if (i > 0) {
                System.out.print(", ");
            }
            System.out.print(board.toSan(moveEvaluation.getMove()) + " "
                    + formatEvaluation(moveEvaluation.getEvaluation()));
        }
        System.out.println();
    }

    private static String formatEvaluation(int evaluation) {
        return String.format("%+d", evaluation);
    }

    private static boolean choosePlayerColor(Scanner scanner) {
        while (true) {
            System.out.print("Play as white or black? ");
            String choice = scanner.nextLine().trim().toLowerCase();
            if (choice.equals("white") || choice.equals("w")) {
                return true;
            }
            if (choice.equals("black") || choice.equals("b")) {
                return false;
            }
            System.out.println("Enter 'white' or 'black'.");
        }
    }

    private static boolean promptPlayAgain(Scanner scanner, List<String> sanMoves, String result,
                                           String startingFen, int startingMoveNumber) {
        while (true) {
            System.out.print("Play again, export PGN, or quit? ");
            String choice = scanner.nextLine().trim().toLowerCase();
            if (choice.equals("play again") || choice.equals("play") || choice.equals("again")
                    || choice.equals("yes") || choice.equals("y")) {
                System.out.println();
                return true;
            }
            if (choice.equals("export") || choice.equals("pgn") || choice.equals("/pgn")) {
                System.out.println(buildPgn(sanMoves, result, startingFen, startingMoveNumber));
                System.out.println();
                continue;
            }
            if (choice.equals("quit") || choice.equals("q") || choice.equals("no") || choice.equals("n")) {
                return false;
            }
            System.out.println("Enter 'play again', 'pgn', or 'quit'.");
        }
    }

    private static String buildPgn(List<String> sanMoves, String result, String startingFen, int startingMoveNumber) {
        StringBuilder pgn = new StringBuilder();
        pgn.append("[Event \"Casual Game\"]\n")
           .append("[Site \"?\"]\n")
           .append("[Date \"????.??.??\"]\n")
           .append("[Round \"?\"]\n")
           .append("[White \"White\"]\n")
           .append("[Black \"Black\"]\n")
           .append("[Result \"").append(result).append("\"]\n");

        if (!startingFen.isEmpty()) {
            pgn.append("[SetUp \"1\"]\n")
               .append("[FEN \"").append(startingFen).append("\"]\n");
        }

        pgn.append('\n').append(buildMoveText(sanMoves, result, startingFen, startingMoveNumber));
        return pgn.toString();
    }

    private static String buildMoveText(List<String> sanMoves, String result, String startingFen, int startingMoveNumber) {
        StringBuilder moveText = new StringBuilder();
        boolean startsWithBlack = startsWithBlackToMove(startingFen);

        for (int i = 0; i < sanMoves.size(); i++) {
            int ply = startsWithBlack ? i + 1 : i;
            int moveNumber = startingMoveNumber + (ply / 2);

            if (ply % 2 == 0) {
                appendToken(moveText, moveNumber + ".");
            } else if (i == 0) {
                appendToken(moveText, moveNumber + "...");
            }

            appendToken(moveText, sanMoves.get(i));
        }

        appendToken(moveText, result);
        return moveText.toString();
    }

    private static void appendToken(StringBuilder text, String token) {
        if (!text.isEmpty()) {
            text.append(' ');
        }
        text.append(token);
    }

    private static boolean startsWithBlackToMove(String fen) {
        String[] fields = fen.trim().split("\\s+");
        return fields.length > 1 && fields[1].equals("b");
    }

    private static int getStartingMoveNumber(String fen) {
        String[] fields = fen.trim().split("\\s+");
        if (fields.length <= 5) {
            return 1;
        }

        try {
            return Integer.parseInt(fields[5]);
        } catch (NumberFormatException ex) {
            return 1;
        }
    }
}
