//v2.4

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

public class SugorokuClient extends JFrame {
    private int myId = 0, currentTurn = 1;
    private int numPlayers = 2;
    private int[] px = new int[0], py = new int[0], pmoney = new int[0], pitem = new int[0];
    private boolean[] pdebt = new boolean[0];
    private int goalX, goalY;
    private boolean gameOver = false;

    private static final Color[] PLAYER_COLORS = { Color.BLUE, Color.RED, new Color(0, 150, 0), new Color(255, 140, 0), Color.MAGENTA, new Color(0, 180, 180) };

    private Board board = new Board(10, 10);
    private JLabel infoLabel;
    private BoardPanel boardPanel;
    private JTextArea logArea;

    private JButton btnRoll, btnItem, btnUp, btnDown, btnLeft, btnRight;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private DicePanel dicePanel;
    private int diceValue = 1;

    private Image moneyImg;
    private Image cardImg;
    private Image goalImg;

    public SugorokuClient(String host, int port) throws IOException {
        setTitle("2D通信ボードゲーム - 物件＆復帰機能搭載");
        setSize(1000, 750);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        infoLabel = new JLabel("サーバーに接続中...", JLabel.CENTER);
        infoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        add(infoLabel, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        add(boardPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        JPanel actionPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        btnRoll = new JButton("🎲 サイコロを振る");
        btnItem = new JButton("🃏 特急カードを使う");
        btnRoll.setEnabled(false); btnItem.setEnabled(false);

        btnRoll.addActionListener(e -> sendCommand("ROLL"));
        btnItem.addActionListener(e -> sendCommand("ITEM"));
        actionPanel.add(btnRoll); actionPanel.add(btnItem);

        JPanel movePanel = new JPanel(new BorderLayout());
        btnUp = new JButton("↑"); btnDown = new JButton("↓");
        btnLeft = new JButton("←"); btnRight = new JButton("→");
        setMoveButtonsEnabled(false);

        dicePanel = new DicePanel();
        dicePanel.setPreferredSize(new Dimension(80, 80));

        try {
            moneyImg = makeWhiteTransparent(ImageIO.read(new File("money.png")));
            cardImg = makeWhiteTransparent(ImageIO.read(new File("card.jpg")));
            goalImg = makeWhiteTransparent(ImageIO.read(new File("goal.jpg")));
        } catch (Exception e) {
            System.out.println("画像が見つかりません。アイコンなしで描画します。");
        }

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
        controls.add(dicePanel);
        bottomPanel.add(controls, BorderLayout.WEST);

        logArea = new JTextArea(8, 30);
        logArea.setEditable(false);
        JScrollPane scroll = new JScrollPane(logArea);
        bottomPanel.add(scroll, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
        new Thread(new ServerReceiver()).start();
    }

    private void sendCommand(String cmd) {
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
                    else if (line.startsWith("BOARD_INIT ")) {
                        String[] mainParts = line.split(" ");
                        int w = Integer.parseInt(mainParts[1]);
                        int h = Integer.parseInt(mainParts[2]);
                        board = new Board(w, h);

                        String[] tiles = mainParts[3].split(",");
                        int idx = 0;
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                board.setTile(x, y, Integer.parseInt(tiles[idx++]));
                            }
                        }
                        if (mainParts.length > 4) {
                            String[] stations = mainParts[4].split("\\|");
                            for(String s : stations) {
                                if(s.isEmpty()) continue;
                                String[] parts = s.split(",");
                                board.setStationName(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
                            }
                        }
                    }
                    else if (line.startsWith("UPDATE ")) {
                        String[] t = line.split(" ");
                        int p = 0;
                        currentTurn = Integer.parseInt(t[++p]);
                        numPlayers = Integer.parseInt(t[++p]);

                        int[] nx = new int[numPlayers], ny = new int[numPlayers];
                        int[] nmoney = new int[numPlayers], nitem = new int[numPlayers];
                        boolean[] ndebt = new boolean[numPlayers];
                        for (int i = 0; i < numPlayers; i++) {
                            nx[i] = Integer.parseInt(t[++p]); ny[i] = Integer.parseInt(t[++p]);
                            nmoney[i] = Integer.parseInt(t[++p]); nitem[i] = Integer.parseInt(t[++p]);
                            ndebt[i] = Integer.parseInt(t[++p]) != 0;
                        }
                        goalX = Integer.parseInt(t[++p]); goalY = Integer.parseInt(t[++p]);
                        px = nx; py = ny; pmoney = nmoney; pitem = nitem; pdebt = ndebt;

                        SwingUtilities.invokeLater(() -> {
                            StringBuilder sb = new StringBuilder(String.format("【第%d期】 自分:社長%d", currentTurn, myId));
                            for (int i = 0; i < numPlayers; i++) {
                                sb.append(String.format(" | P%d: %d円%s (ｶｰﾄﾞ%d)", i + 1, pmoney[i], pdebt[i] ? "(借金)" : "", pitem[i]));
                            }
                            infoLabel.setText(sb.toString());
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
                            int idx = myId - 1;
                            if (idx >= 0 && idx < numPlayers && pitem[idx] > 0) {
                                btnItem.setEnabled(true);
                            }
                        });
                    }
                    else if (line.startsWith("CHOOSE_DIR ")) {
                        int steps = Integer.parseInt(line.substring(11));
                        SwingUtilities.invokeLater(() -> {
                            logArea.append("＞ 残り " + steps + " 歩です。方向を選んでください。\n");
                            setMoveButtonsEnabled(true);
                        });
                    }
                    else if (line.startsWith("DICE ")) {
                        int n = Integer.parseInt(line.substring(5));
                        SwingUtilities.invokeLater(() -> showDice(n));
                    }
                    else if (line.startsWith("ASK_BUY ")) {
                        handleAskBuy(line.substring(8));
                    }
                    else if (line.startsWith("ASK_SELL ")) {
                        handleAskSell(line.substring(9));
                    }
                    else if (line.startsWith("GAMEOVER ")) {
                        String ranking = line.substring(9);
                        SwingUtilities.invokeLater(() -> showGameOver(ranking));
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> logArea.append("サーバーとの通信が切断されました。\n"));
            }
        }
    }

    private void handleAskBuy(String payload) {
        SwingUtilities.invokeLater(() -> {
            String[] parts = payload.split(" ");
            String stationName = parts[0];
            String[] props = parts[1].split(",");
            
            JPanel panel = new JPanel(new GridLayout(props.length + 1, 1));
            panel.add(new JLabel("【" + stationName + "駅】購入する物件を選んでください。"));
            JCheckBox[] boxes = new JCheckBox[props.length];
            
            for (int i = 0; i < props.length; i++) {
                String[] pData = props[i].split(":");
                boxes[i] = new JCheckBox(pData[1] + " (価格:" + pData[2] + "円 / 収益率:" + (int)(Double.parseDouble(pData[3])*100) + "%)");
                boxes[i].setActionCommand(pData[0]);
                panel.add(boxes[i]);
            }
            
            int result = JOptionPane.showConfirmDialog(this, panel, "物件の購入", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
                StringBuilder sb = new StringBuilder("BUY ");
                for (JCheckBox box : boxes) {
                    if (box.isSelected()) sb.append(box.getActionCommand()).append(",");
                }
                out.println(sb.toString());
            } else {
                out.println("BUY NONE");
            }
        });
    }

    private void handleAskSell(String payload) {
        SwingUtilities.invokeLater(() -> {
            String[] props = payload.split(",");
            String[] options = new String[props.length];
            String[] ids = new String[props.length];
            
            for (int i = 0; i < props.length; i++) {
                String[] pData = props[i].split(":");
                ids[i] = pData[0];
                options[i] = pData[1] + " (売却益: " + pData[2] + "円)";
            }
            
            String selected = (String) JOptionPane.showInputDialog(this, 
                "借金返済のため、売却する物件を選んでください", "強制売却", 
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                
            if (selected != null) {
                for (int i = 0; i < options.length; i++) {
                    if (options[i].equals(selected)) {
                        out.println("SELL " + ids[i]);
                        return;
                    }
                }
            }
            out.println("SELL NONE");
        });
    }

    private void showGameOver(String ranking) {
        gameOver = true;
        btnRoll.setEnabled(false); btnItem.setEnabled(false); setMoveButtonsEnabled(false);
        StringBuilder sb = new StringBuilder("【最終決算】\n");
        String[] entries = ranking.split(",");
        for (int i = 0; i < entries.length; i++) {
            String[] kv = entries[i].split(":");
            sb.append((i + 1)).append("位: ").append(kv[0]).append(" (").append(kv[1]).append("円)\n");
        }
        JOptionPane.showMessageDialog(this, sb.toString(), "ゲーム終了", JOptionPane.INFORMATION_MESSAGE);
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

                    // ===== 背景色 =====
                    if (x == goalX && y == goalY) g2.setColor(new Color(255, 100, 255));
                    else if (tile == 1) g2.setColor(new Color(150, 200, 255));
                    else if (tile == 2) g2.setColor(new Color(255, 150, 150));
                    else if (tile == 3) g2.setColor(new Color(255, 230, 100));
                    else if (tile == 4) g2.setColor(new Color(180, 130, 220));
                    else if (tile == 5) g2.setColor(new Color(140, 230, 140));
                    else g2.setColor(Color.WHITE);

                    g2.fillRect(cx + 2, cy + 2, cellW - 4, cellH - 4);
                    g2.setColor(Color.GRAY);
                    g2.drawRect(cx + 2, cy + 2, cellW - 4, cellH - 4);

                    int iconSize = (int)(Math.min(cellW, cellH) * 0.65);
                    int ix = cx + (cellW - iconSize) / 2;
                    int iy = cy + (cellH - iconSize) / 2;

                    // ===== アイコン & 駅名描画 =====
                    if (x == goalX && y == goalY) {
                        if (goalImg != null) g2.drawImage(goalImg, ix, iy, iconSize, iconSize, this);
                    } else if (tile == 1 || tile == 2) {
                        if (moneyImg != null) g2.drawImage(moneyImg, ix, iy, iconSize, iconSize, this);
                    } else if (tile == 3) {
                        if (cardImg != null) g2.drawImage(cardImg, ix, iy, iconSize, iconSize, this);
                    } else if (tile == 4) {
                        drawPovertyIcon(g2, ix, iy, iconSize);
                    } else if (tile == 5) {
                        g2.setColor(new Color(30, 80, 30));
                        // 【変更点】文字化け対策のため Arial から SansSerif に変更
                        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, cellW / 4));
                        String sName = board.getStationName(x, y);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(sName, cx + (cellW - fm.stringWidth(sName)) / 2, cy + cellH / 2 + fm.getAscent() / 3);
                    }
                }
            }

            int pSize = Math.min(cellW, cellH) / 2;
            for (int i = 0; i < numPlayers && i < px.length; i++) {
                Color c = PLAYER_COLORS[i % PLAYER_COLORS.length];
                int offsetX = (i % 2 == 0) ? 5 : cellW - pSize - 5;
                int offsetY = (i < 2) ? 5 : cellH - pSize - 5;

                g2.setColor(c);
                g2.fillOval(px[i] * cellW + offsetX, py[i] * cellH + offsetY, pSize, pSize);
                g2.setColor(Color.WHITE);
                // 【変更点】文字化け対策のため Arial から SansSerif に変更
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                g2.drawString("P" + (i + 1), px[i] * cellW + offsetX + 2, py[i] * cellH + offsetY + pSize - 3);
            }
        }
    }

    private void drawPovertyIcon(Graphics2D g2, int x, int y, int size) {
        Color prevColor = g2.getColor();
        g2.setColor(new Color(90, 40, 120)); g2.fillOval(x, y, size, size);
        g2.setColor(Color.WHITE); int eyeSize = size / 6;
        g2.fillOval(x + size / 4 - eyeSize / 2, y + size / 3, eyeSize, eyeSize);
        g2.fillOval(x + size * 3 / 4 - eyeSize / 2, y + size / 3, eyeSize, eyeSize);
        g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(2f));
        g2.drawArc(x + size / 4, y + size * 3 / 5, size / 2, size / 4, 200, 140);
        g2.setColor(prevColor);
    }

    private class DicePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int size = Math.min(getWidth(), getHeight()) - 10;
            int x = (getWidth() - size) / 2, y = (getHeight() - size) / 2;
            g2.setColor(Color.WHITE); g2.fillRoundRect(x, y, size, size, 15, 15);
            g2.setColor(Color.BLACK); g2.drawRoundRect(x, y, size, size, 15, 15);
            drawDots(g2, x, y, size, diceValue);
        }
    }

    private void drawDot(Graphics2D g2, int x, int y) { g2.fillOval(x - 5, y - 5, 10, 10); }

    private void drawDots(Graphics2D g2, int x, int y, int s, int n) {
        int l = x + s / 4, c = x + s / 2, r = x + s * 3 / 4;
        int t = y + s / 4, m = y + s / 2, b = y + s * 3 / 4;
        switch (n) {
            case 1: drawDot(g2, c, m); break;
            case 2: drawDot(g2, l, t); drawDot(g2, r, b); break;
            case 3: drawDot(g2, l, t); drawDot(g2, c, m); drawDot(g2, r, b); break;
            case 4: drawDot(g2, l, t); drawDot(g2, r, t); drawDot(g2, l, b); drawDot(g2, r, b); break;
            case 5: drawDot(g2, l, t); drawDot(g2, r, t); drawDot(g2, c, m); drawDot(g2, l, b); drawDot(g2, r, b); break;
            case 6: drawDot(g2, l, t); drawDot(g2, r, t); drawDot(g2, l, m); drawDot(g2, r, m); drawDot(g2, l, b); drawDot(g2, r, b); break;
        }
    }

    private void showDice(int n) { diceValue = n; dicePanel.repaint(); }

    private BufferedImage makeWhiteTransparent(BufferedImage image) {
        BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y); Color c = new Color(rgb);
                if (c.getRed() > 240 && c.getGreen() > 240 && c.getBlue() > 240) result.setRGB(x, y, 0x00000000);
                else result.setRGB(x, y, rgb);
            }
        }
        return result;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { new SugorokuClient(args[0], Integer.parseInt(args[1])).setVisible(true); } 
            catch (Exception e) { e.printStackTrace(); }
        });
    }
}