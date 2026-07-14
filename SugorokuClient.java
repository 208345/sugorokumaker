//v2.0

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class SugorokuClient extends JFrame {
    private int myId = 0, currentTurn = 1;
    private int p1x, p1y, p1money, p1item;
    private int p2x, p2y, p2money, p2item;
    private int goalX, goalY;
    
    private Board board = new Board(10, 10);
    private JLabel infoLabel;
    private BoardPanel boardPanel;
    private JTextArea logArea;
    
    // 操作ボタン
    private JButton btnRoll, btnItem, btnUp, btnDown, btnLeft, btnRight;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public SugorokuClient(String host, int port) throws IOException {
        setTitle("2D通信ボードゲーム");
        setSize(900, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        infoLabel = new JLabel("サーバーに接続中...", JLabel.CENTER);
        infoLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        add(infoLabel, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        add(boardPanel, BorderLayout.CENTER);

        // 下部の操作＆ログパネル構築
        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        // --- アクションボタン（左下） ---
        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        btnRoll = new JButton("🎲 サイコロを振る");
        btnItem = new JButton("🃏 特急カードを使う");
        btnRoll.setEnabled(false); btnItem.setEnabled(false);
        
        btnRoll.addActionListener(e -> sendCommand("ROLL"));
        btnItem.addActionListener(e -> sendCommand("ITEM"));
        actionPanel.add(btnRoll); actionPanel.add(btnItem);
        
        // --- 十字移動ボタン（中央下） ---
        JPanel movePanel = new JPanel(new BorderLayout());
        btnUp = new JButton("↑"); btnDown = new JButton("↓");
        btnLeft = new JButton("←"); btnRight = new JButton("→");
        setMoveButtonsEnabled(false);
        
        btnUp.addActionListener(e -> sendCommand("UP"));
        btnDown.addActionListener(e -> sendCommand("DOWN"));
        btnLeft.addActionListener(e -> sendCommand("LEFT"));
        btnRight.addActionListener(e -> sendCommand("RIGHT"));
        
        movePanel.add(btnUp, BorderLayout.NORTH);
        movePanel.add(btnDown, BorderLayout.SOUTH);
        movePanel.add(btnLeft, BorderLayout.WEST);
        movePanel.add(btnRight, BorderLayout.EAST);
        movePanel.add(new JLabel("移動", SwingConstants.CENTER), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout());
        controls.add(actionPanel);
        controls.add(movePanel);
        bottomPanel.add(controls, BorderLayout.WEST);

        logArea = new JTextArea(8, 40);
        logArea.setEditable(false);
        bottomPanel.add(new JScrollPane(logArea), BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        new Thread(new ServerReceiver()).start();
    }

    private void sendCommand(String cmd) {
        // ボタン連打を防ぐため、一度押したら全ボタンを一旦無効化
        btnRoll.setEnabled(false); btnItem.setEnabled(false);
        setMoveButtonsEnabled(false);
        out.println(cmd);
    }

    private void setMoveButtonsEnabled(boolean enabled) {
        btnUp.setEnabled(enabled); btnDown.setEnabled(enabled);
        btnLeft.setEnabled(enabled); btnRight.setEnabled(enabled);
    }

    private class ServerReceiver implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("INIT ")) {
                        myId = Integer.parseInt(line.substring(5));
                    } 
                    // 【修正箇所】サーバーから送られてきた盤面データで確実に上書きする
                    else if (line.startsWith("BOARD_INIT ")) {
                        String[] tokens = line.split(" ");
                        int w = Integer.parseInt(tokens[1]);
                        int h = Integer.parseInt(tokens[2]);
                        
                        // サーバーから指定されたサイズで盤面を作り直す
                        board = new Board(w, h);
                        
                        // サーバーから送られてきたマスの種類（カンマ区切り）を配列に
                        String[] tiles = tokens[3].split(",");
                        int idx = 0;
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                // 1マスずつ、クライアントの盤面データに種類（0〜3）をセットしていく
                                board.setTile(x, y, Integer.parseInt(tiles[idx]));
                                idx++;
                            }
                        }
                    }
                    else if (line.startsWith("UPDATE ")) {
                        String[] t = line.split(" ");
                        currentTurn = Integer.parseInt(t[1]);
                        p1x = Integer.parseInt(t[2]); p1y = Integer.parseInt(t[3]);
                        p1money = Integer.parseInt(t[4]); p1item = Integer.parseInt(t[5]);
                        p2x = Integer.parseInt(t[6]); p2y = Integer.parseInt(t[7]);
                        p2money = Integer.parseInt(t[8]); p2item = Integer.parseInt(t[9]);
                        goalX = Integer.parseInt(t[10]); goalY = Integer.parseInt(t[11]);
                        
                        SwingUtilities.invokeLater(() -> {
                            infoLabel.setText(String.format("【第%d期】 自分:社長%d  |  P1: %d円 (カード%d)  |  P2: %d円 (カード%d)", 
                                currentTurn, myId, p1money, p1item, p2money, p2item));
                            boardPanel.repaint();
                        });
                    } 
                    else if (line.startsWith("MSG ")) {
                        String msg = line.substring(4);
                        SwingUtilities.invokeLater(() -> {
                            logArea.append(msg + "\n");
                            logArea.setCaretPosition(logArea.getDocument().getLength());
                        });
                    } 
                    else if (line.equals("YOUR_TURN")) {
                        SwingUtilities.invokeLater(() -> {
                            btnRoll.setEnabled(true);
                            if (myId == 1 && p1item > 0) btnItem.setEnabled(true);
                            if (myId == 2 && p2item > 0) btnItem.setEnabled(true);
                        });
                    }
                    else if (line.startsWith("CHOOSE_DIR ")) {
                        int steps = Integer.parseInt(line.substring(11));
                        SwingUtilities.invokeLater(() -> {
                            logArea.append("＞ 残り " + steps + " 歩です。方向を選んでください。\n");
                            setMoveButtonsEnabled(true);
                        });
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> logArea.append("切断されました。\n"));
            }
        }
    }

    private class BoardPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = board.getWidth();
            int h = board.getHeight();
            int cellW = getWidth() / w;
            int cellH = getHeight() / h;

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int cx = x * cellW;
                    int cy = y * cellH;

                    int tile = board.getTile(x, y);
                    if (tile == 1) g2.setColor(new Color(150, 200, 255)); // 青マス
                    else if (tile == 2) g2.setColor(new Color(255, 150, 150)); // 赤マス
                    else if (tile == 3) g2.setColor(new Color(255, 230, 100)); // 黄マス
                    else g2.setColor(Color.WHITE); // 白マス

                    // 目的地の場合は星色
                    if (x == goalX && y == goalY) g2.setColor(new Color(255, 100, 255));

                    g2.fillRect(cx + 2, cy + 2, cellW - 4, cellH - 4);
                    g2.setColor(Color.LIGHT_GRAY);
                    g2.drawRect(cx + 2, cy + 2, cellW - 4, cellH - 4);

                    if (x == goalX && y == goalY) {
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("Arial", Font.BOLD, 16));
                        g2.drawString("GOAL", cx + 10, cy + cellH / 2 + 5);
                    }
                }
            }

            // プレイヤー描画
            int pSize = Math.min(cellW, cellH) / 2;
            g2.setColor(Color.BLUE);
            g2.fillOval(p1x * cellW + 5, p1y * cellH + 5, pSize, pSize);
            g2.setColor(Color.WHITE);
            g2.drawString("P1", p1x * cellW + 10, p1y * cellH + 20);

            g2.setColor(Color.RED);
            g2.fillOval(p2x * cellW + cellW - pSize - 5, p2y * cellH + cellH - pSize - 5, pSize, pSize);
            g2.setColor(Color.WHITE);
            g2.drawString("P2", p2x * cellW + cellW - pSize, p2y * cellH + cellH - 10);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new SugorokuClient(args[0], Integer.parseInt(args[1])).setVisible(true);
            } catch (Exception e) { e.printStackTrace(); }
        });
    }
}