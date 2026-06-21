import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

public class GUI {
    // Shared opening book for both the user-vs-engine game and the
    // engine-vs-engine match. Loaded from the "polyglot.book" system property
    // or a "book.bin" in the working directory; null (and ignored everywhere)
    // when no book is configured, so behaviour is unchanged without one.
    private static final PolyglotBook OPENING_BOOK = PolyglotBook.openDefault();

    // Time controls offered for user-vs-engine games: base time plus a per-move
    // Fischer increment (both in milliseconds).
    private enum TimeControl {
        UNTIMED("Untimed", -1L, -1L),
        ONE_MIN("1 min", 1 * 60_000L, 0L),
        ONE_ONE("1|1", 1 * 60_000L, 1_000L),
        THREE_MIN("3 min", 3 * 60_000L, 0L),
        THREE_TWO("3|2", 3 * 60_000L, 2_000L),
        FIVE_MIN("5 min", 5 * 60_000L, 0L),
        FIVE_TWO("5|2", 5 * 60_000L, 2_000L),
        TEN_MIN("10 min", 10 * 60_000L, 0L),
        FIFTEEN_TEN("15|10", 15 * 60_000L, 10_000L);

        private final String label;
        private final long baseMillis;
        private final long incrementMillis;

        TimeControl(String label, long baseMillis, long incrementMillis) {
            this.label = label;
            this.baseMillis = baseMillis;
            this.incrementMillis = incrementMillis;
        }

        private boolean isUntimed() {
            return baseMillis < 0;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Chess");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setResizable(false);
            ChessBoardPanel boardPanel = new ChessBoardPanel();
            SidePanel sidePanel = new SidePanel(boardPanel);
            EngineMatchPanel engineMatchPanel = new EngineMatchPanel(boardPanel);
            boardPanel.setSidePanel(sidePanel);

            JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.add(engineMatchPanel, BorderLayout.WEST);
            mainPanel.add(boardPanel, BorderLayout.CENTER);
            mainPanel.add(sidePanel, BorderLayout.EAST);

            frame.add(mainPanel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private static class SidePanel extends JPanel {
        private static final int PANEL_WIDTH = 226;
        private static final int PANEL_HEIGHT = 640;

        private final JLabel turnLabel;
        private final JComboBox<TimeControl> timeControlCombo;
        private final JCheckBox engineCheckBox;
        private final JLabel engineClockLabel;
        private final JLabel playerClockLabel;
        private final JTextArea moveHistoryArea;

        private SidePanel(ChessBoardPanel boardPanel) {
            setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
            setBackground(new Color(238, 238, 238));
            setLayout(null);

            turnLabel = new JLabel("White to move.");
            turnLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
            turnLabel.setBounds(20, 10, 200, 18);
            add(turnLabel);

            timeControlCombo = new JComboBox<>(TimeControl.values());
            timeControlCombo.setSelectedItem(TimeControl.UNTIMED);
            timeControlCombo.setFont(new Font("SansSerif", Font.PLAIN, 12));
            timeControlCombo.setFocusable(false);
            timeControlCombo.setBounds(20, 32, 200, 24);
            timeControlCombo.setToolTipText("Time control for the next game");
            add(timeControlCombo);

            engineCheckBox = new JCheckBox("Engine", true);
            engineCheckBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
            engineCheckBox.setBackground(getBackground());
            engineCheckBox.setFocusable(false);
            engineCheckBox.setBounds(20, 60, 200, 20);
            engineCheckBox.addActionListener(event -> boardPanel.onEngineSettingChanged());
            add(engineCheckBox);

            addButton("Play White", 20, 84, boardPanel::playWhite);
            addButton("Play Black", 20, 112, boardPanel::playBlack);
            addButton("New Game", 20, 140, boardPanel::newGame);
            addButton("Flip Board", 20, 168, boardPanel::flipBoard);

            Font clockFont = new Font("Monospaced", Font.BOLD, 16);
            engineClockLabel = new JLabel("Engine   --:--");
            engineClockLabel.setFont(clockFont);
            engineClockLabel.setBounds(20, 198, 200, 26);
            add(engineClockLabel);

            playerClockLabel = new JLabel("You      --:--");
            playerClockLabel.setFont(clockFont);
            playerClockLabel.setBounds(20, 226, 200, 26);
            add(playerClockLabel);

            JPanel moveListPanel = new JPanel();
            moveListPanel.setLayout(new BorderLayout());
            moveListPanel.setBackground(Color.WHITE);
            moveListPanel.setBorder(BorderFactory.createLineBorder(new Color(130, 150, 170)));
            moveListPanel.setBounds(20, 258, 200, 372);

            moveHistoryArea = new JTextArea();
            moveHistoryArea.setEditable(false);
            moveHistoryArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
            moveHistoryArea.setLineWrap(true);
            moveHistoryArea.setWrapStyleWord(true);
            moveHistoryArea.setMargin(new java.awt.Insets(8, 8, 8, 8));

            JScrollPane scrollPane = new JScrollPane(moveHistoryArea);
            scrollPane.setBorder(null);
            moveListPanel.add(scrollPane, BorderLayout.CENTER);
            add(moveListPanel);
        }

        private TimeControl getSelectedTimeControl() {
            return (TimeControl) timeControlCombo.getSelectedItem();
        }

        private void setStatusMessage(String text) {
            turnLabel.setText(text);
        }

        private void setEngineClock(String time, boolean running, boolean low) {
            engineClockLabel.setText("Engine   " + time);
            engineClockLabel.setForeground(clockColor(running, low));
        }

        private void setPlayerClock(String time, boolean running, boolean low) {
            playerClockLabel.setText("You      " + time);
            playerClockLabel.setForeground(clockColor(running, low));
        }

        private Color clockColor(boolean running, boolean low) {
            if (low) {
                return new Color(200, 40, 40);
            }
            return running ? new Color(20, 20, 20) : new Color(130, 130, 130);
        }

        private void addButton(String text, int x, int y, Runnable action) {
            JButton button = new JButton(text);
            button.setFont(new Font("SansSerif", Font.BOLD, 12));
            button.setFocusable(false);
            button.setBounds(x, y, 200, 26);
            button.addActionListener(event -> action.run());
            add(button);
        }

        private boolean isEngineEnabled() {
            return engineCheckBox.isSelected();
        }

        private void setWhiteToMove(boolean whiteToMove) {
            turnLabel.setText(whiteToMove ? "White to move." : "Black to move.");
        }

        private void setEngineThinking(boolean engineThinking) {
            if (engineThinking) {
                turnLabel.setText("Engine Thinking...");
            }
        }

        private void setMoveHistory(List<String> moveHistory) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < moveHistory.size(); i++) {
                if (i % 2 == 0) {
                    if (!text.isEmpty()) {
                        text.append('\n');
                    }
                    text.append((i / 2) + 1).append(". ");
                } else {
                    text.append(' ');
                }
                text.append(moveHistory.get(i));
            }
            moveHistoryArea.setText(text.toString());
            moveHistoryArea.setCaretPosition(moveHistoryArea.getDocument().getLength());
        }
    }

    private static class EngineMatchPanel extends JPanel {
        private static final int PANEL_WIDTH = 260;
        private static final int PANEL_HEIGHT = 640;

        private final ChessBoardPanel boardPanel;
        private final JComboBox<String> gamesCombo;
        private final JTextField timeField;
        private final JRadioButton headlessRadio;
        private final JRadioButton nearlyHeadlessRadio;
        private final JRadioButton guiRadio;
        private final JCheckBox concurrentMatchesCheckBox;
        private final JSpinner concurrentThreadsSpinner;
        private final JButton startButton;
        private final JButton stopButton;
        private final JTextArea matchLogArea;
        private final JLabel statusLabel;
        private EngineMatchWorker worker;

        private EngineMatchPanel(ChessBoardPanel boardPanel) {
            this.boardPanel = boardPanel;
            setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
            setBackground(new Color(245, 245, 245));
            setLayout(null);

            JLabel titleLabel = new JLabel("Engine vs Engine");
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            titleLabel.setBounds(16, 14, 220, 24);
            add(titleLabel);

            JLabel matchCountLabel = new JLabel("Games to play:");
            matchCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            matchCountLabel.setBounds(16, 48, 220, 20);
            add(matchCountLabel);

            gamesCombo = new JComboBox<>(new String[]{"5", "10", "50", "100", "200", "500", "1000", "Infinite"});
            gamesCombo.setBounds(16, 72, 120, 28);
            add(gamesCombo);

            JLabel timeLabel = new JLabel("Time per move (ms):");
            timeLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            timeLabel.setBounds(16, 110, 220, 20);
            add(timeLabel);

            timeField = new JTextField("50");
            timeField.setBounds(16, 134, 120, 28);
            add(timeField);

            JLabel infoLabel = new JLabel("Information level:");
            infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            infoLabel.setBounds(16, 176, 220, 20);
            add(infoLabel);

            headlessRadio = new JRadioButton("Headless");
            headlessRadio.setBackground(getBackground());
            headlessRadio.setFont(new Font("SansSerif", Font.PLAIN, 12));
            headlessRadio.setBounds(16, 200, 220, 22);
            headlessRadio.setSelected(true);
            add(headlessRadio);

            nearlyHeadlessRadio = new JRadioButton("Nearly headless");
            nearlyHeadlessRadio.setBackground(getBackground());
            nearlyHeadlessRadio.setFont(new Font("SansSerif", Font.PLAIN, 12));
            nearlyHeadlessRadio.setBounds(16, 224, 220, 22);
            add(nearlyHeadlessRadio);

            concurrentMatchesCheckBox = new JCheckBox("Run matches concurrently");
            concurrentMatchesCheckBox.setBackground(getBackground());
            concurrentMatchesCheckBox.setFont(new Font("SansSerif", Font.PLAIN, 12));
            concurrentMatchesCheckBox.setBounds(16, 256, 220, 22);
            concurrentMatchesCheckBox.addActionListener(event -> updateConcurrencyControlState());
            add(concurrentMatchesCheckBox);

            JLabel threadsLabel = new JLabel("Threads:");
            threadsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            threadsLabel.setBounds(16, 284, 60, 20);
            add(threadsLabel);

            concurrentThreadsSpinner = new JSpinner(new SpinnerNumberModel(16, 1, 64, 1));
            concurrentThreadsSpinner.setBounds(80, 282, 60, 28);
            add(concurrentThreadsSpinner);

            guiRadio = new JRadioButton("GUI");
            guiRadio.setBackground(getBackground());
            guiRadio.setFont(new Font("SansSerif", Font.PLAIN, 12));
            guiRadio.setBounds(16, 318, 220, 22);
            add(guiRadio);

            ButtonGroup displayGroup = new ButtonGroup();
            displayGroup.add(headlessRadio);
            displayGroup.add(nearlyHeadlessRadio);
            displayGroup.add(guiRadio);

            headlessRadio.addActionListener(event -> updateConcurrencyControlState());
            nearlyHeadlessRadio.addActionListener(event -> updateConcurrencyControlState());
            guiRadio.addActionListener(event -> updateConcurrencyControlState());

            startButton = new JButton("Start Match");
            startButton.setFont(new Font("SansSerif", Font.BOLD, 12));
            startButton.setBounds(16, 352, 120, 30);
            startButton.addActionListener(event -> startMatch());
            add(startButton);

            stopButton = new JButton("Stop");
            stopButton.setFont(new Font("SansSerif", Font.BOLD, 12));
            stopButton.setBounds(154, 352, 94, 30);
            stopButton.setEnabled(false);
            stopButton.addActionListener(event -> stopMatch());
            add(stopButton);

            statusLabel = new JLabel("Ready.");
            statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
            statusLabel.setBounds(16, 394, 220, 20);
            add(statusLabel);

            JPanel logPanel = new JPanel(new BorderLayout());
            logPanel.setBackground(Color.WHITE);
            logPanel.setBorder(BorderFactory.createTitledBorder("Match Log / PGN"));
            logPanel.setBounds(16, 424, 228, 240);

            matchLogArea = new JTextArea();
            matchLogArea.setEditable(false);
            matchLogArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
            matchLogArea.setLineWrap(true);
            matchLogArea.setWrapStyleWord(true);
            matchLogArea.setMargin(new java.awt.Insets(8, 8, 8, 8));

            JScrollPane logScroll = new JScrollPane(matchLogArea);
            logScroll.setBorder(null);
            logPanel.add(logScroll, BorderLayout.CENTER);
            add(logPanel);
        }

        private void startMatch() {
            if (worker != null && !worker.isDone()) {
                return;
            }

            String gamesSelection = (String) gamesCombo.getSelectedItem();
            boolean infinite = "Infinite".equals(gamesSelection);
            int gameCount;
            if (infinite) {
                gameCount = 0;   // unused while infinite; loop runs until stopped
            } else {
                try {
                    gameCount = Integer.parseInt(gamesSelection);
                } catch (NumberFormatException ex) {
                    gameCount = 10;
                }
            }

            int timeLimit;
            try {
                timeLimit = Integer.parseInt(timeField.getText().trim());
                if (timeLimit <= 0) {
                    timeLimit = 50;
                }
            } catch (NumberFormatException ex) {
                timeLimit = 50;
            }

            DisplayMode displayMode = headlessRadio.isSelected() ? DisplayMode.HEADLESS
                    : nearlyHeadlessRadio.isSelected() ? DisplayMode.NEARLY_HEADLESS
                    : DisplayMode.GUI;

            matchLogArea.setText("");
            setStatus("Starting engine match...");
            setButtonsEnabled(false);
            boardPanel.setMatchActive(true);

            boolean concurrentMatches = concurrentMatchesCheckBox.isSelected() && displayMode != DisplayMode.GUI;
            int threadCount;
            try {
                threadCount = ((Number) concurrentThreadsSpinner.getValue()).intValue();
                if (threadCount <= 0) {
                    threadCount = Runtime.getRuntime().availableProcessors();
                }
            } catch (Exception ex) {
                threadCount = Runtime.getRuntime().availableProcessors();
            }

            matchLogArea.setText("");
            setStatus("Starting engine match...");
            setButtonsEnabled(false);
            boardPanel.setMatchActive(true);

            worker = new EngineMatchWorker(gameCount, infinite, timeLimit, displayMode, concurrentMatches, threadCount);
            worker.execute();
        }

        private void stopMatch() {
            if (worker != null) {
                worker.cancel(true);
            }
        }

        private void setButtonsEnabled(boolean enabled) {
            startButton.setEnabled(enabled);
            stopButton.setEnabled(!enabled);
            gamesCombo.setEnabled(enabled);
            timeField.setEnabled(enabled);
            headlessRadio.setEnabled(enabled);
            nearlyHeadlessRadio.setEnabled(enabled);
            guiRadio.setEnabled(enabled);
            concurrentMatchesCheckBox.setEnabled(enabled && !guiRadio.isSelected());
            concurrentThreadsSpinner.setEnabled(enabled && concurrentMatchesCheckBox.isSelected() && !guiRadio.isSelected());
        }

        private void updateConcurrencyControlState() {
            boolean enabled = !guiRadio.isSelected();
            concurrentMatchesCheckBox.setEnabled(enabled);
            concurrentThreadsSpinner.setEnabled(enabled && concurrentMatchesCheckBox.isSelected());
        }

        private void setStatus(String text) {
            statusLabel.setText(text);
        }

        private void appendLog(String text) {
            matchLogArea.append(text + "\n");
            matchLogArea.setCaretPosition(matchLogArea.getDocument().getLength());
        }

        private enum DisplayMode {
            HEADLESS,
            NEARLY_HEADLESS,
            GUI
        }

        private class EngineMatchWorker extends SwingWorker<Void, String> {
            private final int totalGames;
            private final boolean infinite;
            private final int timeLimit;
            private final DisplayMode displayMode;
            private final boolean concurrentMatches;
            private final int threadCount;
            private int boardEngineWins;
            private int oldEngineWins;
            private int draws;
            private int gamesCompleted;
            private final List<String> headlessResults = new ArrayList<>();
            // Opening-position state: a Random shared across the match (seeded from
            // current time for variety) and the FEN shared by each same-position pair.
            private final Random openingRng = new Random();
            private String currentPairFen = null;

            private EngineMatchWorker(int totalGames, boolean infinite, int timeLimit, DisplayMode displayMode,
                                      boolean concurrentMatches, int threadCount) {
                this.totalGames = totalGames;
                this.infinite = infinite;
                this.timeLimit = timeLimit;
                this.displayMode = displayMode;
                this.concurrentMatches = concurrentMatches;
                this.threadCount = threadCount;
            }

            // Picks a random opening FEN from the book, or returns null (startpos) if
            // no book is configured so existing behaviour is preserved.
            private String pickOpeningFen() {
                if (OPENING_BOOK == null || !OPENING_BOOK.isEnabled()) {
                    return null;
                }
                return OPENING_BOOK.randomOpeningFen(openingRng);
            }

            @Override
            protected Void doInBackground() {
                headlessResults.clear();
                boardEngineWins = 0;
                oldEngineWins = 0;
                draws = 0;
                gamesCompleted = 0;

                if (concurrentMatches && (displayMode == DisplayMode.HEADLESS || displayMode == DisplayMode.NEARLY_HEADLESS)) {
                    // Pool size: in finite mode never exceed the number of games; in
                    // infinite mode saturate up to the requested thread count.
                    int poolSize = infinite ? threadCount : Math.min(threadCount, totalGames);
                    ExecutorService executor = Executors.newFixedThreadPool(poolSize);
                    try {
                        // Finite: one batch of exactly totalGames tasks. Infinite: repeat
                        // batches of poolSize until the user stops. Cancellation is checked
                        // between batches and between futures.
                        int nextGameIndex = 1;
                        boolean keepRunning = true;
                        while (keepRunning && !isCancelled()) {
                            int batchSize = infinite ? poolSize : totalGames;
                            // Pre-generate opening FENs for this batch: one per pair of
                            // games (odd+even share the same position, colors swapped).
                            int numPairs = (batchSize + 1) / 2;
                            String[] pairFens = new String[numPairs];
                            for (int p = 0; p < numPairs; p++) {
                                pairFens[p] = pickOpeningFen();
                            }
                            List<Callable<String>> tasks = new ArrayList<>();
                            for (int k = 0; k < batchSize; k++) {
                                final int index = nextGameIndex++;
                                final boolean boardPlaysWhite = (index % 2) == 1;
                                // Map game index to its pair slot within this batch.
                                final String openingFen = pairFens[(k) / 2];
                                tasks.add(() -> runSingleGame(index, boardPlaysWhite, openingFen));
                            }

                            List<Future<String>> futures = executor.invokeAll(tasks);
                            for (Future<String> future : futures) {
                                if (!consumeFuture(future, executor)) {
                                    keepRunning = false;
                                    break;
                                }
                            }

                            if (!infinite) {
                                keepRunning = false;   // finite match is a single batch
                            } else if (keepRunning) {
                                publish("status:Running infinite match... games completed: " + gamesCompleted);
                            }
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        executor.shutdownNow();
                    }
                } else {
                    for (int gameIndex = 1; (infinite || gameIndex <= totalGames) && !isCancelled(); gameIndex++) {
                        boolean boardPlaysWhite = (gameIndex % 2) == 1;
                        // Draw a new opening position at the start of each pair (odd game).
                        // Both games in the pair use the same position with colors swapped.
                        if (boardPlaysWhite) {
                            currentPairFen = pickOpeningFen();
                        }
                        publish("status:Running game " + gameIndex + (infinite ? "" : " of " + totalGames));
                        String gameResult = runSingleGame(gameIndex, boardPlaysWhite, currentPairFen);
                        if (!isRealResult(gameResult)) {
                            break;   // interrupted before this game finished; don't count it
                        }
                        recordResult(gameResult);
                        gamesCompleted++;
                        if (displayMode == DisplayMode.HEADLESS) {
                            headlessResults.add(gameResult);
                        } else {
                            publish("game:" + gameResult);
                        }
                    }
                }

                // The status, any batched headless results, and the match summary are
                // rendered in done() rather than published here. A cancelled
                // SwingWorker silently drops pending process() chunks, so publishing
                // the summary at this point would lose it whenever the user interrupts
                // the match -- done() always runs and can append directly.
                return null;
            }

            private boolean isRealResult(String gameResult) {
                return gameResult != null
                        && (gameResult.contains("Draw") || gameResult.contains("Win for"));
            }

            // Consumes one finished game future: records it if it is a real result.
            // Returns false if the worker has been cancelled/interrupted and the
            // caller should stop consuming further futures.
            private boolean consumeFuture(Future<String> future, ExecutorService executor) {
                if (isCancelled()) {
                    executor.shutdownNow();
                    return false;
                }

                String gameResult;
                try {
                    gameResult = future.get();
                } catch (InterruptedException ex) {
                    executor.shutdownNow();
                    Thread.currentThread().interrupt();
                    return false;
                } catch (ExecutionException ex) {
                    gameResult = "Game error: " + ex.getCause();
                }

                if (isRealResult(gameResult)) {   // skip interrupted/errored games
                    recordResult(gameResult);
                    gamesCompleted++;
                    if (displayMode == DisplayMode.HEADLESS) {
                        headlessResults.add(gameResult);
                    } else {
                        publish("game:" + gameResult);
                    }
                }
                return true;
            }

            private void recordResult(String gameResult) {
                if (gameResult.contains("Draw")) {
                    draws++;
                } else if (gameResult.contains("Win for New Engine")) {
                    boardEngineWins++;
                } else if (gameResult.contains("Win for Old Engine")) {
                    oldEngineWins++;
                }
            }

            private void appendMatchSummary(boolean interrupted) {
                appendLog("--- Match summary" + (interrupted ? " (interrupted)" : "") + " ---");
                appendLog("New Engine wins: " + boardEngineWins);
                appendLog("Old Engine wins: " + oldEngineWins);
                appendLog("Draws: " + draws);
                if (infinite) {
                    appendLog("Total games played: " + gamesCompleted
                            + (interrupted ? " (match stopped)" : ""));
                } else if (interrupted) {
                    appendLog("Games played: " + gamesCompleted + " of " + totalGames + " (match stopped early)");
                } else {
                    appendLog("Total games played: " + gamesCompleted);
                }
            }

            private String runSingleGame(int gameIndex, boolean boardPlaysWhite, String openingFen) {
                Board currentBoard = new Board();
                if (openingFen != null && !openingFen.isEmpty()) {
                    currentBoard.loadFromFen(openingFen);
                }
                List<String> history = new ArrayList<>();

                while (!isCancelled() && !currentBoard.isDraw() && !currentBoard.getLegalMoves().isEmpty()) {
                    boolean boardToMove = currentBoard.whiteToMove == boardPlaysWhite;
                    String engineMove = findBestMove(currentBoard, boardToMove, timeLimit);
                    if (engineMove == null || engineMove.isEmpty()) {
                        break;
                    }

                    String san = currentBoard.toSan(engineMove);
                    currentBoard.movePiece(engineMove);
                    history.add(san);

                    if (displayMode == DisplayMode.GUI) {
                        publish("update:" + currentBoard.toFen() + "|" + String.join(",", history));
                    }
                }

                String result;
                if (currentBoard.isDraw()) {
                    result = "Game " + gameIndex + ": Draw";
                } else if (currentBoard.getLegalMoves().isEmpty()) {
                    boolean boardWon = boardPlaysWhite ? !currentBoard.whiteToMove : currentBoard.whiteToMove;
                    String winner = boardWon ? "New Engine" : "Old Engine";
                    String color = boardWon == boardPlaysWhite ? "White" : "Black";
                    result = "Game " + gameIndex + ": Win for " + winner + " (" + color + ")";
                } else {
                    result = "Game " + gameIndex + ": Unknown result";
                }

                if (displayMode != DisplayMode.HEADLESS) {
                    publish("log:PGN " + gameIndex + ": " + buildPgn(history, openingFen));
                }
                return result;
            }

            private String findBestMove(Board position, boolean boardToMove, int timeLimitMillis) {
                if (boardToMove) {
                    Board searchBoard = new Board();
                    searchBoard.loadFromFen(position.toFen());
                    String bookMove = OPENING_BOOK == null ? null : OPENING_BOOK.probe(searchBoard);
                    if (bookMove != null) {
                        return bookMove;
                    }
                    return searchBoard.getSearchReport(1, timeLimitMillis, 64, null, 0, null, 0).getBestMove();
                }

                BoardOld searchBoardOld = new BoardOld();
                if (!searchBoardOld.loadFromFen(position.toFen())) {
                    return null;
                }
                // BoardOld mirrors Board's public API; probe via FEN + legal moves
                // so the book stays decoupled from the specific board class.
                String bookMove = OPENING_BOOK == null ? null
                        : OPENING_BOOK.probe(searchBoardOld.toFen(), searchBoardOld.getLegalMoves());
                if (bookMove != null) {
                    return bookMove;
                }
                return searchBoardOld.getSearchReport(1, timeLimitMillis, 64, null, 0, null, 0).getBestMove();
            }

            private String buildPgn(List<String> moves, String openingFen) {
                StringBuilder pgn = new StringBuilder();
                boolean hasOpening = openingFen != null && !openingFen.isEmpty()
                        && !openingFen.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq");
                if (hasOpening) {
                    pgn.append("[SetUp \"1\"] [FEN \"").append(openingFen).append("\"] ");
                }
                // Derive the starting move number from the FEN fullmove field so the
                // PGN move numbers are correct when the opening is mid-game.
                int startMoveNumber = 1;
                if (hasOpening) {
                    String[] fields = openingFen.trim().split("\\s+");
                    if (fields.length >= 6) {
                        try { startMoveNumber = Integer.parseInt(fields[5]); }
                        catch (NumberFormatException ignored) { }
                    }
                }
                // If the opening position has Black to move, the first listed move
                // needs an ellipsis.
                boolean startsBlack = hasOpening && openingFen.contains(" b ");
                for (int i = 0; i < moves.size(); i++) {
                    int ply = startsBlack ? i + 1 : i;
                    int moveNum = startMoveNumber + (ply / 2);
                    if (ply % 2 == 0) {
                        pgn.append(moveNum).append(". ");
                    } else if (i == 0) {
                        pgn.append(moveNum).append("... ");
                    }
                    pgn.append(moves.get(i));
                    if (i + 1 < moves.size()) {
                        pgn.append(' ');
                    }
                }
                return pgn.toString();
            }

            @Override
            protected void process(List<String> chunks) {
                for (String chunk : chunks) {
                    if (chunk.startsWith("status:")) {
                        setStatus(chunk.substring(7));
                    } else if (chunk.startsWith("game:")) {
                        appendLog(chunk.substring(5));
                    } else if (chunk.startsWith("log:")) {
                        appendLog(chunk.substring(4));
                    } else if (chunk.startsWith("update:")) {
                        String payload = chunk.substring(7);
                        int separator = payload.indexOf('|');
                        if (separator > 0) {
                            String fen = payload.substring(0, separator);
                            String historyText = payload.substring(separator + 1);
                            Board displayBoard = new Board();
                            if (displayBoard.loadFromFen(fen)) {
                                List<String> history = new ArrayList<>();
                                if (!historyText.isEmpty()) {
                                    for (String move : historyText.split(",")) {
                                        history.add(move);
                                    }
                                }
                                boardPanel.setBoardState(displayBoard, history);
                            }
                        }
                    }
                }
            }

            @Override
            protected void done() {
                boardPanel.setMatchActive(false);
                boolean interrupted = isCancelled();

                // Flush any batched (headless) per-game results that were not streamed
                // live. For the streaming display modes this list is empty, so nothing
                // is duplicated.
                for (String result : headlessResults) {
                    appendLog(result);
                }

                // Render the summary directly here (not via publish) so it always
                // appears, including when the match was interrupted by the user.
                appendMatchSummary(interrupted);

                if (interrupted) {
                    setStatus(infinite
                            ? "Match stopped after " + gamesCompleted + " games."
                            : "Match interrupted after " + gamesCompleted + " of " + totalGames + " games.");
                } else {
                    setStatus("Match complete.");
                }
                setButtonsEnabled(true);
            }
        }
    }

    private static class ChessBoardPanel extends JPanel {
        private static final int SQUARE_SIZE = 80;
        private static final int BOARD_SIZE = SQUARE_SIZE * 8;
        private static final int PIECE_PADDING = 6;
        private static final Color LIGHT_SQUARE = new Color(238, 238, 210);
        private static final Color DARK_SQUARE = new Color(118, 150, 86);
        private static final Color SELECTED_SQUARE = new Color(246, 246, 105, 170);
        private static final Color LEGAL_MOVE_DOT = new Color(30, 30, 30, 70);
        private static final Color LEGAL_CAPTURE = new Color(30, 30, 30, 55);

        private Board board;
        private SidePanel sidePanel;
        private final List<String> moveHistory = new ArrayList<>();
        private final Map<Character, Image> pieceImages = new HashMap<>();
        private String selectedSquare;
        private String draggedSquare;
        private char draggedPiece = '.';
        private int dragX;
        private int dragY;
        private boolean playerIsWhite = true;
        private boolean whitePerspective = true;
        private boolean engineThinking;
        private boolean engineMatchActive;
        private int engineRequestId;
        private int activeEngineRequestId;

        // ---- Chess clock (user-vs-engine games) ----
        private TimeControl timeControl = TimeControl.FIVE_MIN;
        private long playerTimeMillis;
        private long engineTimeMillis;
        private long incrementMillis;
        private boolean clockActive;
        private boolean clockStarted;
        private boolean gameOver;
        private long turnStartMillis;
        private final Timer clockTimer;

        private ChessBoardPanel() {
            board = new Board();
            loadPieceImages();
            setPreferredSize(new Dimension(BOARD_SIZE, BOARD_SIZE));
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    startDrag(event.getX(), event.getY());
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    updateDrag(event.getX(), event.getY());
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    finishDrag(event.getX(), event.getY());
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);

            clockTimer = new Timer(150, event -> onClockTick());
            clockTimer.start();
        }

        private void setSidePanel(SidePanel sidePanel) {
            this.sidePanel = sidePanel;
            startClock();
            updateTurnLabel();
        }

        private void playWhite() {
            engineRequestId++;
            activeEngineRequestId = 0;
            engineThinking = false;
            playerIsWhite = true;
            whitePerspective = true;
            selectedSquare = null;
            clearDrag();
            startClock();
            updateTurnLabel();
            repaint();
            maybeStartEngineMove();
        }

        private void playBlack() {
            engineRequestId++;
            activeEngineRequestId = 0;
            engineThinking = false;
            playerIsWhite = false;
            whitePerspective = false;
            selectedSquare = null;
            clearDrag();
            startClock();
            updateTurnLabel();
            repaint();
            maybeStartEngineMove();
        }

        private void newGame() {
            engineRequestId++;
            activeEngineRequestId = 0;
            board = new Board();
            moveHistory.clear();
            selectedSquare = null;
            clearDrag();
            engineThinking = false;
            startClock();
            updateTurnLabel();
            updateMoveHistory();
            repaint();
            maybeStartEngineMove();
        }

        private void flipBoard() {
            whitePerspective = !whitePerspective;
            selectedSquare = null;
            clearDrag();
            repaint();
        }

        private void onEngineSettingChanged() {
            engineRequestId++;
            activeEngineRequestId = 0;
            engineThinking = false;
            clearDrag();
            updateTurnLabel();
            maybeStartEngineMove();
        }

        private void startDrag(int x, int y) {
            if (gameOver || engineThinking || isEngineTurn() || engineMatchActive) {
                return;
            }

            String clickedSquare = getSquareAt(x, y);
            if (clickedSquare == null) {
                return;
            }

            char piece = getPieceAt(clickedSquare);
            if (piece == '.' || !hasLegalMoveFrom(clickedSquare)) {
                return;
            }

            selectedSquare = clickedSquare;
            draggedSquare = clickedSquare;
            draggedPiece = piece;
            dragX = x;
            dragY = y;
            repaint();
        }

        private void updateDrag(int x, int y) {
            if (draggedSquare == null) {
                return;
            }

            dragX = x;
            dragY = y;
            repaint();
        }

        private void finishDrag(int x, int y) {
            if (draggedSquare == null) {
                return;
            }

            String destinationSquare = getSquareAt(x, y);
            String move = destinationSquare == null ? null : findLegalMove(draggedSquare, destinationSquare);
            if (move != null && !gameOver) {
                addMoveToHistory(move);
                board.movePiece(move);
                applyMoveToClock(true);
                updateTurnLabel();
                updateMoveHistory();
                if (!checkGameEnd()) {
                    maybeStartEngineMove();
                }
            }

            clearDrag();
            repaint();
        }

        private void clearDrag() {
            selectedSquare = null;
            draggedSquare = null;
            draggedPiece = '.';
        }

        private String findLegalMove(String fromSquare, String toSquare) {
            String moveStart = fromSquare + toSquare;
            List<String> legalMoves = board.getLegalMoves();
            if (legalMoves.contains(moveStart)) {
                return moveStart;
            }

            List<Character> promotionOptions = new ArrayList<>();
            for (char promotionPiece : new char[]{'q', 'r', 'b', 'n'}) {
                if (legalMoves.contains(moveStart + promotionPiece)) {
                    promotionOptions.add(promotionPiece);
                }
            }
            if (promotionOptions.isEmpty()) {
                return null;
            }

            char chosen = choosePromotionPiece(promotionOptions);
            if (chosen == '\0') {
                return null;
            }
            return moveStart + chosen;
        }

        // Asks the user which piece to promote to, shown as piece icons (falling back
        // to text). Returns the chosen promotion char, or '\0' if the user cancels.
        private char choosePromotionPiece(List<Character> options) {
            boolean whitePromoting = board.whiteToMove;
            char[] order = {'q', 'r', 'b', 'n'};
            String[] names = {"Queen", "Rook", "Bishop", "Knight"};

            List<Object> buttonLabels = new ArrayList<>();
            List<Character> buttonPieces = new ArrayList<>();
            for (int i = 0; i < order.length; i++) {
                if (!options.contains(order[i])) {
                    continue;
                }
                char coloredPiece = whitePromoting ? Character.toUpperCase(order[i]) : order[i];
                Image image = pieceImages.get(coloredPiece);
                buttonLabels.add(image != null ? new ImageIcon(image) : names[i]);
                buttonPieces.add(order[i]);
            }

            Object[] choices = buttonLabels.toArray();
            int selected = JOptionPane.showOptionDialog(
                    this,
                    "Promote pawn to:",
                    "Pawn Promotion",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.PLAIN_MESSAGE,
                    null,
                    choices,
                    choices.length > 0 ? choices[0] : null);

            if (selected < 0 || selected >= buttonPieces.size()) {
                return '\0';
            }
            return buttonPieces.get(selected);
        }

        private boolean hasLegalMoveFrom(String square) {
            for (String move : board.getLegalMoves()) {
                if (move.startsWith(square)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isLegalDestination(String square) {
            if (selectedSquare == null) {
                return false;
            }

            String moveStart = selectedSquare + square;
            for (String move : board.getLegalMoves()) {
                if (move.startsWith(moveStart)) {
                    return true;
                }
            }
            return false;
        }

        private String getSquareAt(int x, int y) {
            if (x < 0 || x >= BOARD_SIZE || y < 0 || y >= BOARD_SIZE) {
                return null;
            }

            int displayFile = x / SQUARE_SIZE;
            int displayRank = y / SQUARE_SIZE;
            int file = whitePerspective ? displayFile : 7 - displayFile;
            int rank = whitePerspective ? 7 - displayRank : displayRank;
            return String.valueOf((char) ('a' + file)) + (char) ('1' + rank);
        }

        private boolean isEngineTurn() {
            return sidePanel != null
                    && sidePanel.isEngineEnabled()
                    && board.whiteToMove != playerIsWhite;
        }

        private void maybeStartEngineMove() {
            if (sidePanel == null || gameOver || !isEngineTurn() || engineThinking || board.getLegalMoves().isEmpty()) {
                return;
            }

            engineThinking = true;
            clearDrag();
            updateTurnLabel();
            repaint();
            int requestId = ++engineRequestId;
            activeEngineRequestId = requestId;
            final String searchFen = board.toFen();
            final int engineBudgetMillis = computeEngineBudgetMillis();
            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    // The search must run on a private board: the engine now uses
                    // make/unmake and mutates the board in place while searching, so
                    // it can no longer share the live display board with the Swing
                    // event-dispatch thread (which paints it concurrently).
                    Board searchBoard = new Board();
                    if (!searchBoard.loadFromFen(searchFen)) {
                        return null;
                    }
                    String bookMove = OPENING_BOOK == null ? null : OPENING_BOOK.probe(searchBoard);
                    if (bookMove != null) {
                        return bookMove;
                    }
                    return searchBoard.getSearchReport(1, engineBudgetMillis, 64, null, 0, null, 0).getBestMove();
                }

                @Override
                protected void done() {
                    try {
                        String engineMove = get();
                        if (requestId == engineRequestId
                                && !gameOver
                                && sidePanel.isEngineEnabled()
                                && board.whiteToMove != playerIsWhite
                                && engineMove != null
                                && !engineMove.isEmpty()) {
                            addMoveToHistory(engineMove);
                            board.movePiece(engineMove);
                            applyMoveToClock(false);
                            updateMoveHistory();
                        }
                    } catch (Exception ex) {
                        // Leave the current board untouched if the background search fails.
                    }

                    if (requestId == activeEngineRequestId) {
                        engineThinking = false;
                        activeEngineRequestId = 0;
                    }
                    updateTurnLabel();
                    repaint();
                    if (!checkGameEnd()) {
                        maybeStartEngineMove();
                    }
                }
            }.execute();
        }

        private void updateTurnLabel() {
            if (sidePanel != null) {
                if (engineThinking) {
                    sidePanel.setEngineThinking(true);
                } else {
                    sidePanel.setWhiteToMove(board.whiteToMove);
                }
            }
        }

        private void addMoveToHistory(String move) {
            moveHistory.add(board.toSan(move));
        }

        private void updateMoveHistory() {
            if (sidePanel != null) {
                sidePanel.setMoveHistory(moveHistory);
            }
        }

        public void setBoardState(Board newBoard, List<String> newHistory) {
            this.board = newBoard;
            this.moveHistory.clear();
            if (newHistory != null) {
                this.moveHistory.addAll(newHistory);
            }
            this.selectedSquare = null;
            this.draggedSquare = null;
            this.draggedPiece = '.';
            this.engineThinking = false;
            updateMoveHistory();
            updateTurnLabel();
            repaint();
        }

        public void setMatchActive(boolean active) {
            this.engineMatchActive = active;
            if (active) {
                clockActive = false;
                gameOver = false;
                refreshClockLabels();
            }
        }

        // ---- Chess clock ----

        private void startClock() {
            if (engineMatchActive) {
                return;
            }
            if (sidePanel != null) {
                timeControl = sidePanel.getSelectedTimeControl();
            }
            clockActive = !timeControl.isUntimed();
            clockStarted = false;
            gameOver = false;
            if (clockActive) {
                playerTimeMillis = timeControl.baseMillis;
                engineTimeMillis = timeControl.baseMillis;
                incrementMillis = timeControl.incrementMillis;
            }
            refreshClockLabels();
        }

        // Called right after a move is played on the board. The clock only begins
        // counting once the first move of the game has been made (so a freshly
        // launched or freshly set-up game is not ticking while the player waits).
        // After that, the side that just moved is charged its time and gets the
        // increment, and the clock switches to the other side.
        private void applyMoveToClock(boolean moverWasPlayer) {
            if (!clockActive) {
                return;
            }
            long now = System.currentTimeMillis();
            if (!clockStarted) {
                clockStarted = true;
                turnStartMillis = now;
                refreshClockLabels();
                return;
            }
            long elapsed = now - turnStartMillis;
            if (moverWasPlayer) {
                playerTimeMillis = Math.max(0, playerTimeMillis - elapsed) + incrementMillis;
            } else {
                engineTimeMillis = Math.max(0, engineTimeMillis - elapsed) + incrementMillis;
            }
            turnStartMillis = now;
            refreshClockLabels();
        }

        private boolean runningSideIsPlayer() {
            return board.whiteToMove == playerIsWhite;
        }

        private long liveRemaining(boolean player) {
            long base = player ? playerTimeMillis : engineTimeMillis;
            if (clockActive && clockStarted && !gameOver && runningSideIsPlayer() == player) {
                base -= System.currentTimeMillis() - turnStartMillis;
            }
            return Math.max(0, base);
        }

        private void onClockTick() {
            if (!clockActive || !clockStarted || gameOver) {
                return;
            }
            boolean playerRunning = runningSideIsPlayer();
            if (liveRemaining(playerRunning) <= 0) {
                handleFlag(playerRunning);
                return;
            }
            refreshClockLabels();
        }

        private void handleFlag(boolean playerFlagged) {
            if (playerFlagged) {
                playerTimeMillis = 0;
            } else {
                engineTimeMillis = 0;
            }
            clockActive = false;
            gameOver = true;
            engineThinking = false;
            engineRequestId++;
            activeEngineRequestId = 0;
            refreshClockLabels();
            if (sidePanel != null) {
                sidePanel.setStatusMessage(playerFlagged
                        ? "You ran out of time \u2014 Engine wins."
                        : "Engine ran out of time \u2014 You win!");
            }
            repaint();
        }

        // Per-move time budget for the engine. Untimed games use a fixed default;
        // timed games derive the budget from the engine's remaining clock so it
        // never flags in fast games yet uses plenty of time when the clock is large.
        // In both cases the opening is played quickly, saving time for the middlegame.
        private int computeEngineBudgetMillis() {
            long budget;
            if (!clockActive) {
                budget = 2500;
            } else {
                long remaining = engineTimeMillis;
                int movesToGo = 30;
                budget = remaining / movesToGo + (incrementMillis * 4) / 5;
                long cap = remaining / 3;
                if (budget > cap) {
                    budget = cap;
                }
                long safetyMargin = 250;
                long maxSafe = remaining - safetyMargin;
                if (budget > maxSafe) {
                    budget = maxSafe;
                }
            }
            budget = (long) (budget * openingPhaseFactor());
            if (budget < 20) {
                budget = 20;
            }
            return (int) budget;
        }

        // Obvious opening moves get a fraction of the normal budget, ramping up to
        // full time by the time the game reaches the middlegame.
        private double openingPhaseFactor() {
            int ply = moveHistory.size();
            if (ply < 8) {
                return 0.25;
            }
            if (ply < 20) {
                return 0.25 + 0.75 * (ply - 8) / 12.0;
            }
            return 1.0;
        }

        private boolean checkGameEnd() {
            if (gameOver) {
                return true;
            }
            if (board.getLegalMoves().isEmpty()) {
                clockActive = false;
                gameOver = true;
                String message;
                if (board.isCheckmate()) {
                    boolean playerWins = board.whiteToMove != playerIsWhite;
                    message = playerWins ? "Checkmate \u2014 You win!" : "Checkmate \u2014 Engine wins.";
                } else {
                    message = "Stalemate \u2014 draw.";
                }
                if (sidePanel != null) {
                    sidePanel.setStatusMessage(message);
                }
                refreshClockLabels();
                repaint();
                return true;
            }
            if (board.isDraw()) {
                clockActive = false;
                gameOver = true;
                if (sidePanel != null) {
                    sidePanel.setStatusMessage("Draw.");
                }
                refreshClockLabels();
                return true;
            }
            return false;
        }

        private void refreshClockLabels() {
            if (sidePanel == null) {
                return;
            }
            if (!clockActive) {
                sidePanel.setEngineClock("Untimed", false, false);
                sidePanel.setPlayerClock("Untimed", false, false);
                return;
            }
            long playerRemaining = liveRemaining(true);
            long engineRemaining = liveRemaining(false);
            boolean running = clockStarted && !gameOver;
            boolean playerRunning = running && runningSideIsPlayer();
            boolean engineRunning = running && !runningSideIsPlayer();
            sidePanel.setEngineClock(formatClock(engineRemaining), engineRunning, clockStarted && engineRemaining <= 10_000);
            sidePanel.setPlayerClock(formatClock(playerRemaining), playerRunning, clockStarted && playerRemaining <= 10_000);
        }

        private String formatClock(long millis) {
            if (millis < 0) {
                millis = 0;
            }
            long totalSeconds = millis / 1000;
            if (millis < 10_000) {
                long tenths = (millis % 1000) / 100;
                return totalSeconds + "." + tenths;
            }
            long minutes = totalSeconds / 60;
            long seconds = totalSeconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }

        private char getPieceAt(String square) {
            char[][] pieces = getPiecesFromFen();
            int file = square.charAt(0) - 'a';
            int rank = square.charAt(1) - '1';
            return pieces[rank][file];
        }

        private char[][] getPiecesFromFen() {
            char[][] pieces = new char[8][8];
            for (int rank = 0; rank < 8; rank++) {
                for (int file = 0; file < 8; file++) {
                    pieces[rank][file] = '.';
                }
            }

            String placement = board.toFen().split("\\s+")[0];
            String[] ranks = placement.split("/");
            for (int fenRank = 0; fenRank < ranks.length; fenRank++) {
                int boardRank = 7 - fenRank;
                int file = 0;
                for (int i = 0; i < ranks[fenRank].length(); i++) {
                    char symbol = ranks[fenRank].charAt(i);
                    if (Character.isDigit(symbol)) {
                        file += symbol - '0';
                    } else if (file < 8) {
                        pieces[boardRank][file] = symbol;
                        file++;
                    }
                }
            }

            return pieces;
        }

        private void loadPieceImages() {
            loadPieceImage('P', "wp.png");
            loadPieceImage('N', "wn.png");
            loadPieceImage('B', "wb.png");
            loadPieceImage('R', "wr.png");
            loadPieceImage('Q', "wq.png");
            loadPieceImage('K', "wk.png");
            loadPieceImage('p', "bp.png");
            loadPieceImage('n', "bn.png");
            loadPieceImage('b', "bb.png");
            loadPieceImage('r', "br.png");
            loadPieceImage('q', "bq.png");
            loadPieceImage('k', "bk.png");
        }

        private void loadPieceImage(char piece, String fileName) {
            try {
                Image rawImage = null;
                java.net.URL resource = GUI.class.getResource("/" + fileName);
                if (resource != null) {
                    rawImage = ImageIO.read(resource);
                } else {
                    File sourceRootFile = new File("Chess Engine/src/" + fileName);
                    if (sourceRootFile.exists()) {
                        rawImage = ImageIO.read(sourceRootFile);
                    } else {
                        File workingDirectoryFile = new File(fileName);
                        if (workingDirectoryFile.exists()) {
                            rawImage = ImageIO.read(workingDirectoryFile);
                        }
                    }
                }

                if (rawImage != null) {
                    pieceImages.put(piece, createScaledImage(rawImage,
                            SQUARE_SIZE - (PIECE_PADDING * 2),
                            SQUARE_SIZE - (PIECE_PADDING * 2)));
                }
            } catch (IOException ex) {
                pieceImages.remove(piece);
            }
        }

        private BufferedImage createScaledImage(Image image, int width, int height) {
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = scaled.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(image, 0, 0, width, height, null);
            g2.dispose();
            return scaled;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            char[][] pieces = getPiecesFromFen();

            for (int displayRank = 0; displayRank < 8; displayRank++) {
                for (int file = 0; file < 8; file++) {
                    int x = file * SQUARE_SIZE;
                    int y = displayRank * SQUARE_SIZE;
                    int boardFile = whitePerspective ? file : 7 - file;
                    int boardRank = whitePerspective ? 7 - displayRank : displayRank;
                    String square = String.valueOf((char) ('a' + boardFile)) + (char) ('1' + boardRank);

                    g.setColor((file + displayRank) % 2 == 0 ? LIGHT_SQUARE : DARK_SQUARE);
                    g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);

                    if (square.equals(selectedSquare)) {
                        g.setColor(SELECTED_SQUARE);
                        g.fillRect(x, y, SQUARE_SIZE, SQUARE_SIZE);
                    }

                    if (isLegalDestination(square)) {
                        drawLegalMoveHighlight(g, getPieceAt(square), x, y);
                    }

                    if (!square.equals(draggedSquare)) {
                        drawPiece(g, pieces[boardRank][boardFile], x, y);
                    }
                }
            }

            if (draggedPiece != '.') {
                drawPiece(g, draggedPiece, dragX - (SQUARE_SIZE / 2), dragY - (SQUARE_SIZE / 2));
            }
        }

        private void drawLegalMoveHighlight(Graphics2D g, char targetPiece, int x, int y) {
            if (targetPiece == '.') {
                g.setColor(LEGAL_MOVE_DOT);
                int dotSize = 22;
                int dotX = x + (SQUARE_SIZE - dotSize) / 2;
                int dotY = y + (SQUARE_SIZE - dotSize) / 2;
                g.fillOval(dotX, dotY, dotSize, dotSize);
                return;
            }

            g.setColor(LEGAL_CAPTURE);
            g.fillOval(x + 6, y + 6, SQUARE_SIZE - 12, SQUARE_SIZE - 12);
        }

        private void drawPiece(Graphics2D g, char piece, int x, int y) {
            if (piece == '.') {
                return;
            }

            Image image = pieceImages.get(piece);
            if (image == null) {
                return;
            }

            g.drawImage(image, x + PIECE_PADDING, y + PIECE_PADDING,
                    SQUARE_SIZE - (PIECE_PADDING * 2), SQUARE_SIZE - (PIECE_PADDING * 2), this);
        }
    }
}
