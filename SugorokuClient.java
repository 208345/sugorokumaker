// v2.7

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

class ClientProperty {
    int id;
    String name;
    int price;
    double rate;
    int ownerId = 0;
}

public class SugorokuClient extends JFrame {
    private int myId = 0, currentTurn = 1;
    private int numPlayers = 2;
    private int[] px = new int[0], py = new int[0], pmoney = new int[0], pitem = new int[0];
    private boolean[] pdebt = new boolean[0];
    private int[] passet = new int[0]; 
    private int goalX, goalY;
    private boolean gameOver = false;

    private static final Color[] PLAYER_COLORS = { Color.BLUE, Color.RED, new Color(0, 150, 0), new Color(255, 140, 0), Color.MAGENTA, new Color(0, 180, 180) };

    private Board board = new Board(10, 10);
    private Map<Integer, ClientProperty> propMaster = new HashMap<>(); 

    private JLabel infoLabel;
    private BoardPanel boardPanel;
    private JTextArea logArea;

    private JButton btnRoll, btnItem, btnUp, btnDown, btnLeft, btnRight, btnPropList;
    private JLabel turnIndicator;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    
    private DicePanel dicePanel;
    private int[] diceValues = {1};
    private int[] finalDiceValues = {1};
    private boolean isMyDiceRoll = false; 
    
    private javax.swing.Timer diceTimer;
    private int rollCount;

    private Image moneyImg, cardImg, goalImg;

    public SugorokuClient(String host, int port) throws IOException {
        setTitle("2D通信ボードゲーム - 総合アップデート版(v2.14)");
        setSize(1100, 750); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        infoLabel = new JLabel("サーバーに接続中...", JLabel.CENTER);
        infoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        add(infoLabel, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        add(boardPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());

        turnIndicator = new JLabel("待機中...", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        turnIndicator.setOpaque(false);
        turnIndicator.setBackground(Color.LIGHT_GRAY);
        turnIndicator.setForeground(Color.WHITE);
        turnIndicator.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

        JPanel actionPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        btnRoll = new RoundedButton("🎲 サイコロを振る");
        btnItem = new RoundedButton("🃏 特急カードを使う");
        btnPropList = new RoundedButton("🏢 所持物件を見る");
        btnRoll.setEnabled(false); btnItem.setEnabled(false);

        btnRoll.addActionListener(e -> { isMyDiceRoll = true; sendCommand("ROLL"); });
        btnItem.addActionListener(e -> { isMyDiceRoll = true; sendCommand("ITEM"); });
        btnPropList.addActionListener(e -> showMyProperties());
        
        actionPanel.add(btnRoll); 
        actionPanel.add(btnItem);
        actionPanel.add(btnPropList);
        actionPanel.add(turnIndicator);

        JPanel movePanel = new JPanel(new BorderLayout());
        btnUp = new RoundedButton("↑"); btnDown = new RoundedButton("↓");
        btnLeft = new RoundedButton("←"); btnRight = new RoundedButton("→");
        setMoveButtonsEnabled(false);

        dicePanel = new DicePanel();

        try {
            moneyImg = makeWhiteTransparent(ImageIO.read(new File("money.png")));
            cardImg = makeWhiteTransparent(ImageIO.read(new File("card.jpg")));
            goalImg = makeWhiteTransparent(ImageIO.read(new File("goal.jpg")));
        } catch (Exception e) {}

        btnUp.addActionListener(e -> sendCommand("UP"));
        btnDown.addActionListener(e -> sendCommand("DOWN"));
        btnLeft.addActionListener(e -> sendCommand("LEFT"));
        btnRight.addActionListener(e -> sendCommand("RIGHT"));

        movePanel.add(btnUp, BorderLayout.NORTH);
        movePanel.add(btnDown, BorderLayout.SOUTH);
        movePanel.add(btnLeft, BorderLayout.WEST);
        movePanel.add(btnRight, BorderLayout.EAST);
        movePanel.add(new JLabel("移動", SwingConstants.CENTER), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
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
                    // 🌟 追加：再接続時に複数人が切断されていた場合に送られてくる本人確認
                    else if (line.startsWith("ASK_RECONNECT ")) {
                        String[] parts = line.substring(14).split(",");
                        List<String> optionList = new ArrayList<>();
                        List<String> idList = new ArrayList<>();
                        
                        for (String part : parts) {
                            if (part.isEmpty()) continue;
                            String[] kv = part.split(":");
                            idList.add(kv[0]);
                            optionList.add(kv[1]);
                        }
                        
                        String[] options = optionList.toArray(new String[0]);
                        String[] ids = idList.toArray(new String[0]);

                        SwingUtilities.invokeLater(() -> {
                            String selected = (String) JOptionPane.showInputDialog(
                                SugorokuClient.this, 
                                "複数人が切断されています。\nあなたはどのプレイヤーとして復帰しますか？", 
                                "プレイヤーの選択", 
                                JOptionPane.QUESTION_MESSAGE, 
                                null, options, options[0]);
                                
                            if (selected != null) {
                                for (int i = 0; i < options.length; i++) {
                                    if (options[i].equals(selected)) {
                                        out.println("RECONNECT " + ids[i]);
                                        return;
                                    }
                                }
                            }
                            out.println("RECONNECT NONE");
                        });
                    }
                    else if (line.startsWith("PROP_MASTER ")) {
                        String[] props = line.substring(12).split(",");
                        for (String pStr : props) {
                            if (pStr.isEmpty()) continue;
                            String[] d = pStr.split(":");
                            ClientProperty cp = new ClientProperty();
                            cp.id = Integer.parseInt(d[0]);
                            cp.name = d[1];
                            cp.price = Integer.parseInt(d[2]);
                            cp.rate = Double.parseDouble(d[3]);
                            propMaster.put(cp.id, cp);
                        }
                    }
                    else if (line.startsWith("PROP_STATE ")) {
                        String[] states = line.substring(11).split(",");
                        for (String sStr : states) {
                            if (sStr.isEmpty()) continue;
                            String[] d = sStr.split(":");
                            int pid = Integer.parseInt(d[0]);
                            if (propMaster.containsKey(pid)) {
                                propMaster.get(pid).ownerId = Integer.parseInt(d[1]);
                            }
                        }
                    }
                    else if (line.startsWith("BOARD_INIT ")) {
                        String[] mainParts = line.split(" ");
                        int w = Integer.parseInt(mainParts[1]);
                        int h = Integer.parseInt(mainParts[2]);
                        int sx = Integer.parseInt(mainParts[3]);
                        int sy = Integer.parseInt(mainParts[4]);
                        
                        board = new Board(w, h);
                        board.setStartPos(sx, sy);

                        String[] tiles = mainParts[5].split(",");
                        int idx = 0;
                        for (int y = 0; y < h; y++) {
                            for (int x = 0; x < w; x++) {
                                board.setTile(x, y, Integer.parseInt(tiles[idx++]));
                            }
                        }
                        if (mainParts.length > 6) {
                            String[] stations = mainParts[6].split("@");
                            for(String s : stations) {
                                if(s.isEmpty()) continue;
                                String[] parts = s.split(",", 3);
                                board.setStationData(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), parts[2]);
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
                        int[] nasset = new int[numPlayers];
                        
                        for (int i = 0; i < numPlayers; i++) {
                            nx[i] = Integer.parseInt(t[++p]); ny[i] = Integer.parseInt(t[++p]);
                            nmoney[i] = Integer.parseInt(t[++p]); nitem[i] = Integer.parseInt(t[++p]);
                            ndebt[i] = Integer.parseInt(t[++p]) != 0;
                            nasset[i] = Integer.parseInt(t[++p]); 
                        }
                        goalX = Integer.parseInt(t[++p]); goalY = Integer.parseInt(t[++p]);
                        px = nx; py = ny; pmoney = nmoney; pitem = nitem; pdebt = ndebt; passet = nasset;

                        SwingUtilities.invokeLater(() -> {
                            int maxAsset = Integer.MIN_VALUE;
                            for (int a : passet) if (a > maxAsset) maxAsset = a;
                            int maxCount = 0;
                            for (int a : passet) if (a == maxAsset) maxCount++;
                            boolean isTie = (maxCount > 1);

                            int year = (currentTurn - 1) / 4 + 1;
                            String[] SEASONS = {"春", "夏", "秋", "冬"};
                            String season = SEASONS[(currentTurn - 1) % 4];

                            StringBuilder sb = new StringBuilder(String.format("<html>【%d年%s】 自分:社長%d", year, season, myId));
                            for (int i = 0; i < numPlayers; i++) {
                                String color = "black";
                                String prefix = "";
                                if (!isTie && passet[i] == maxAsset) {
                                    color = "#ff8c00";
                                    prefix = "👑&nbsp;";
                                }
                                sb.append(String.format(" | <span style='white-space:nowrap; color:%s;'>%sP%d: %d万円/資%d万円%s (ｶｰﾄﾞ%d)</span>", 
                                    color, prefix, i + 1, pmoney[i], passet[i], pdebt[i] ? "(借金)" : "", pitem[i]));
                            }
                            sb.append("</html>");
                            infoLabel.setText(sb.toString());
                            boardPanel.repaint();
                        });
                    }
                    else if (line.startsWith("MSG_ERR ")) {
                        String errMsg = line.substring(8);
                        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(SugorokuClient.this, errMsg, "エラー", JOptionPane.ERROR_MESSAGE));
                    }
                    else if (line.startsWith("MSG ")) {
                        String msg = line.substring(4);
                        SwingUtilities.invokeLater(() -> {
                            logArea.append(msg + "\n");
                            logArea.setCaretPosition(logArea.getDocument().getLength());

                            if (msg.startsWith("▶ ")) {
                                int endIdx = msg.indexOf(" の番");
                                if (endIdx != -1) {
                                    String activePlayer = msg.substring(2, endIdx);
                                    if (!activePlayer.equals("社長" + myId)) {
                                        turnIndicator.setText(activePlayer + "のターン");
                                        turnIndicator.setBackground(Color.GRAY);
                                    }
                                }
                            }
                        });
                    }
                    else if (line.equals("YOUR_TURN")) {
                        SwingUtilities.invokeLater(() -> {
                            turnIndicator.setText("あなたのターン");
                            turnIndicator.setBackground(new Color(220, 50, 50));
                            btnRoll.setEnabled(true);
                            if (myId - 1 >= 0 && myId - 1 < numPlayers && pitem[myId - 1] > 0) {
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
                        String[] strVals = line.substring(5).split(",");
                        int[] n = new int[strVals.length];
                        for(int i=0; i<strVals.length; i++) n[i] = Integer.parseInt(strVals[i]);
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
                boxes[i] = new JCheckBox(pData[1] + " (価格:" + pData[2] + "万円 / 収益率:" + (int)(Double.parseDouble(pData[3])*100) + "%)");
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
                int ratePercent = (int)(Double.parseDouble(pData[3]) * 100);
                options[i] = pData[1] + " (売却益: " + pData[2] + "万円 / 収益率: " + ratePercent + "%)";
            }
            
            int myMoney = pmoney[myId - 1];
            String msg = "<html>現在の所持金: <font color='red'><b>" + myMoney + "万円</b></font><br>借金返済のため、売却する物件を選んでください</html>";
            
            String selected = (String) JOptionPane.showInputDialog(this, 
                msg, "強制売却", JOptionPane.WARNING_MESSAGE, null, options, options[0]);
                
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

    private void showMyProperties() {
        if (myId == 0) return;
        List<ClientProperty> myProps = new ArrayList<>();
        int myMoney = pmoney[myId - 1];
        int totalAsset = myMoney;

        for (ClientProperty p : propMaster.values()) {
            if (p.ownerId == myId) {
                myProps.add(p);
                totalAsset += p.price;
            }
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body>");
        sb.append("<h3>あなたの所持物件・資産状況</h3>");
        sb.append("<b>現在の所持金:</b> ").append(myMoney).append("万円<br>");
        sb.append("<b>現在の総資産:</b> ").append(totalAsset).append("万円<hr>");
        
        if (myProps.isEmpty()) {
            sb.append("現在所有している物件はありません。");
        } else {
            sb.append("<table border='1' cellspacing='0' cellpadding='4'>");
            sb.append("<tr bgcolor='#e0e0e0'><th>物件名</th><th>価格</th><th>収益率</th><th>年間配当額</th></tr>");
            
            int totalRev = 0; 
            for (ClientProperty p : myProps) {
                int rev = (int)(p.price * p.rate);
                totalRev += rev;
                sb.append("<tr>")
                  .append("<td>").append(p.name).append("</td>")
                  .append("<td align='right'>").append(p.price).append("万円</td>")
                  .append("<td align='right'>").append((int)(p.rate * 100)).append("%</td>")
                  .append("<td align='right'><font color='blue'>+").append(rev).append("万円</font></td>")
                  .append("</tr>");
            }
            sb.append("</table>");
            sb.append("<br><div align='right'><b>次回決算の予想収益合計: <font color='blue'>+").append(totalRev).append("万円</font></b></div>");
        }
        sb.append("</body></html>");
        
        JOptionPane.showMessageDialog(this, new JLabel(sb.toString()), "所持物件一覧", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showGameOver(String ranking) {
        gameOver = true;
        btnRoll.setEnabled(false); btnItem.setEnabled(false); setMoveButtonsEnabled(false);
        StringBuilder sb = new StringBuilder("【最終結果】\n");
        String[] entries = ranking.split(",");
        for (int i = 0; i < entries.length; i++) {
            String[] kv = entries[i].split(":");
            sb.append((i + 1)).append("位: ").append(kv[0]).append(" (総資産: ").append(kv[1]).append("万円)\n");
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

                    if (tile == 6) {
                        g2.setColor(Color.DARK_GRAY);
                        g2.fillRect(cx + 2, cy + 2, cellW - 4, cellH - 4);
                        g2.setColor(Color.GRAY);
                        g2.drawRect(cx + 2, cy + 2, cellW - 4, cellH - 4);
                        
                        if (x == board.getStartX() && y == board.getStartY()) {
                            g2.setColor(Color.RED);
                            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                            g2.drawString("START", cx + 4, cy + cellH - 4);
                        }
                        continue;
                    }

                    if (tile == 1) g2.setColor(new Color(150, 200, 255));
                    else if (tile == 2) g2.setColor(new Color(255, 150, 150));
                    else if (tile == 3) g2.setColor(new Color(255, 230, 100));
                    else if (tile == 4) g2.setColor(new Color(180, 130, 220));
                    else if (tile == 5) g2.setColor(new Color(140, 230, 140)); 
                    else g2.setColor(Color.WHITE);

                    g2.fillRect(cx + 2, cy + 2, cellW - 4, cellH - 4);
                    
                    g2.setStroke(new BasicStroke(1));
                    g2.setColor(Color.GRAY);
                    g2.drawRect(cx + 2, cy + 2, cellW - 4, cellH - 4);

                    if (x == goalX && y == goalY) {
                        g2.setColor(new Color(255, 100, 255));
                        g2.setStroke(new BasicStroke(4));
                        g2.drawRect(cx + 4, cy + 4, cellW - 8, cellH - 8);
                        g2.setStroke(new BasicStroke(1)); 
                    }

                    if (tile == 5) {
                        g2.setColor(new Color(30, 80, 30));
                        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, cellW / 4));
                        String sName = board.getStationName(x, y);
                        FontMetrics fm = g2.getFontMetrics();
                        g2.drawString(sName, cx + (cellW - fm.stringWidth(sName)) / 2, cy + cellH / 2 + fm.getAscent() / 3);
                    }

                    int iconSize = (int)(Math.min(cellW, cellH) * 0.65);
                    int ix = cx + (cellW - iconSize) / 2;
                    int iy = cy + (cellH - iconSize) / 2;

                    if (tile == 1 || tile == 2) {
                        if (moneyImg != null) g2.drawImage(moneyImg, ix, iy, iconSize, iconSize, this);
                    } else if (tile == 3) {
                        if (cardImg != null) g2.drawImage(cardImg, ix, iy, iconSize, iconSize, this);
                    } else if (tile == 4) {
                        drawPovertyIcon(g2, ix, iy, iconSize);
                    }
                    
                    if (x == goalX && y == goalY) {
                        if (goalImg != null) {
                            int smallIconSize = (int)(Math.min(cellW, cellH) * 0.4);
                            g2.drawImage(goalImg, cx + cellW - smallIconSize - 6, cy + 6, smallIconSize, smallIconSize, this);
                        }
                    }
                    
                    if (x == board.getStartX() && y == board.getStartY()) {
                        g2.setColor(Color.RED);
                        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                        g2.drawString("START", cx + 4, cy + cellH - 4);
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
                g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                g2.drawString("P" + (i + 1), px[i] * cellW + offsetX + 2, py[i] * cellH + offsetY + pSize - 3);
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
    }

    private class DicePanel extends JPanel {
        @Override
        public Dimension getPreferredSize() {
            int size = 70;
            int gap = 10;
            int width = gap + diceValues.length * (size + gap);
            return new Dimension(width, 80);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            int size = 70;
            int gap = 10;
            for (int i = 0; i < diceValues.length; i++) {
                int x = gap + i * (size + gap);
                int y = (getHeight() - size) / 2;
                g2.setColor(Color.WHITE); g2.fillRoundRect(x, y, size, size, 15, 15);
                g2.setColor(Color.BLACK); g2.drawRoundRect(x, y, size, size, 15, 15);
                drawDots(g2, x, y, size, diceValues[i]);
            }
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

    private void showDice(int[] n) {
        finalDiceValues = n;
        diceValues = new int[n.length];
        rollCount = 0;

        if (diceTimer != null && diceTimer.isRunning()) {
            diceTimer.stop();
        }

        dicePanel.revalidate();

        diceTimer = new javax.swing.Timer(50, null);
        diceTimer.addActionListener(e -> {
            rollCount++;
            for (int i = 0; i < diceValues.length; i++) {
                diceValues[i] = (int)(Math.random() * 6) + 1;
            }
            dicePanel.repaint();

            if (rollCount > 30) diceTimer.setDelay(diceTimer.getDelay() + 15);

            if (rollCount > 45) {
                diceTimer.stop();
                diceValues = finalDiceValues;
                dicePanel.repaint();
                
                if (isMyDiceRoll) {
                    out.println("DICE_DONE");
                    isMyDiceRoll = false;
                }
            }
        });
        diceTimer.start();
    }

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

    private class RoundedButton extends JButton {
        private boolean isHovered = false;

        public RoundedButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBackground(new Color(240, 240, 240));
            setBorderPainted(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        isHovered = true;
                        repaint();
                    }
                }
                @Override
                public void mouseExited(MouseEvent e) {
                    isHovered = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (getModel().isArmed()) g2.setColor(new Color(200, 200, 200));
            else if (!isEnabled()) g2.setColor(new Color(220, 220, 220));
            else if (isHovered) g2.setColor(new Color(215, 225, 235));
            else g2.setColor(getBackground());
            
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
            g2.dispose();
            super.paintComponent(g);
        }

        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.GRAY);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
            g2.dispose();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { 
                new SugorokuClient(args[0], Integer.parseInt(args[1])).setVisible(true); 
            } catch (Exception e) { 
                e.printStackTrace(); 
            }
        });
    }
}