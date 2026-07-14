import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;

public class SugorokuClient extends JFrame {
    private int myId = 0;
    private int p1Pos = 0;
    private int p2Pos = 0;
    private int currentTurn = 1;

    private JLabel infoLabel;
    private BoardPanel boardPanel;
    private JButton rollButton;
    private JTextArea logArea;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    // 通信確立前にエラーにならないよう、ダミーで生成しておく
    private Board board = new Board(20);
    
    // アニメーション用の変数
    private float p1AnimPos = 0f;
    private float p2AnimPos = 0f;
    private int p1TargetPos = 0;
    private int p2TargetPos = 0;
    private boolean p1Animating = false;
    private boolean p2Animating = false;
    
    // サイコロアニメーション用の変数
    private int diceCurrentValue = 1;
    private boolean diceAnimating = false;
    private int diceAnimCounter = 0;
    private int diceFinalValue = 0;
    
    // コマの移動タイミング制御
    private boolean waitingForDiceStop = false;

    public SugorokuClient(String host, int port) throws IOException {
        setTitle("通信対戦すごろく - リッチUI版");
        setSize(850, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        infoLabel = new JLabel("サーバーに接続中...", JLabel.CENTER);
        infoLabel.setFont(new Font("ユーザーフォント", Font.BOLD, 16));
        infoLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(infoLabel, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        add(boardPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        
        rollButton = new JButton("🎲 サイコロを振る 🎲");
        rollButton.setFont(new Font("ユーザーフォント", Font.BOLD, 18));
        rollButton.setEnabled(false);
        rollButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                out.println("ROLL");
                rollButton.setEnabled(false);
                // サイコロアニメーション開始
                diceAnimating = true;
                diceAnimCounter = 0;
                waitingForDiceStop = true;
            }
        });
        bottomPanel.add(rollButton, BorderLayout.NORTH);

        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        JScrollPane scrollPane = new JScrollPane(logArea);
        bottomPanel.add(scrollPane, BorderLayout.CENTER);

        add(bottomPanel, BorderLayout.SOUTH);

        socket = new Socket(host, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);

        // アニメーション用タイマー開始（50ms毎に更新）
        Timer animationTimer = new Timer(50, e -> updateAnimation());
        animationTimer.start();

        new Thread(new ServerReceiver()).start();
    }

    // アニメーション更新メソッド
    private void updateAnimation() {
        boolean needsRepaint = false;

        // サイコロのアニメーション
        if (diceAnimating) {
            diceAnimCounter++;
            // ランダムにサイコロの目を表示
            diceCurrentValue = (int) (Math.random() * 6) + 1;
            
            // 12フレーム（0.6秒）後にアニメーション停止
            if (diceAnimCounter > 12) {
                diceAnimating = false;
                diceCurrentValue = diceFinalValue;
                waitingForDiceStop = false;  // サイコロ停止、コマのアニメーションを許可
            }
            needsRepaint = true;
        }

        // プレイヤー1のアニメーション（サイコロが止まっている場合のみ）
        if (p1Animating && !waitingForDiceStop) {
            if (p1AnimPos < p1TargetPos) {
                p1AnimPos += 0.2f;
                if (p1AnimPos >= p1TargetPos) {
                    p1AnimPos = p1TargetPos;
                    p1Animating = false;
                }
                needsRepaint = true;
            }
        }

        // プレイヤー2のアニメーション（サイコロが止まっている場合のみ）
        if (p2Animating && !waitingForDiceStop) {
            if (p2AnimPos < p2TargetPos) {
                p2AnimPos += 0.2f;
                if (p2AnimPos >= p2TargetPos) {
                    p2AnimPos = p2TargetPos;
                    p2Animating = false;
                }
                needsRepaint = true;
            }
        }

        if (needsRepaint) {
            boardPanel.repaint();
        }
    }

    private class ServerReceiver implements Runnable {
        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("INIT ")) {
                        myId = Integer.parseInt(line.substring(5));
                        SwingUtilities.invokeLater(() -> infoLabel.setText("あなたは プレイヤー" + myId + " です。対戦相手を待っています..."));
                    } 
                    else if (line.startsWith("BOARD_INIT ")) {
                        String[] tokens = line.split(" ");
                        int length = Integer.parseInt(tokens[1]);
                        String[] eff = tokens[2].split(",");
                        
                        board = new Board(length);
                        for (int i = 0; i < length; i++) {
                            board.setEffect(i, Integer.parseInt(eff[i]));
                        }
                        
                        SwingUtilities.invokeLater(() -> boardPanel.repaint());
                    }
                    else if (line.startsWith("UPDATE ")) {
                        String[] tokens = line.split(" ");
                        currentTurn = Integer.parseInt(tokens[1]);
                        int newP1Pos = Integer.parseInt(tokens[2]);
                        int newP2Pos = Integer.parseInt(tokens[3]);
                        
                        // 新しい位置を即座に設定（画面同期用）
                        p1Pos = newP1Pos;
                        p2Pos = newP2Pos;
                        
                        // アニメーション開始（サイコロが止まっている場合のみ）
                        if (!waitingForDiceStop) {
                            if (newP1Pos != (int)p1AnimPos) {
                                p1AnimPos = (int)p1AnimPos;  // 現在位置に同期
                                p1TargetPos = newP1Pos;
                                p1Animating = true;
                            }
                            if (newP2Pos != (int)p2AnimPos) {
                                p2AnimPos = (int)p2AnimPos;  // 現在位置に同期
                                p2TargetPos = newP2Pos;
                                p2Animating = true;
                            }
                        }
                        
                        SwingUtilities.invokeLater(() -> {
                            infoLabel.setText("【ターン " + currentTurn + "】 あなた: プレイヤー" + myId + "  |  P1: マス" + newP1Pos + "  |  P2: マス" + newP2Pos);
                            boardPanel.repaint();
                        });
                    } 
                    else if (line.startsWith("MSG ")) {
                        String msg = line.substring(4);
                        
                        // サイコロの結果を抽出（「【」と「】」に囲まれた数字）
                        if (msg.contains("【") && msg.contains("】")) {
                            int startIdx = msg.indexOf("【") + 1;
                            int endIdx = msg.indexOf("】");
                            try {
                                diceFinalValue = Integer.parseInt(msg.substring(startIdx, endIdx));
                            } catch (Exception ex) {
                                // 数字の抽出に失敗した場合はスキップ
                            }
                        }
                        
                        SwingUtilities.invokeLater(() -> {
                            logArea.append(msg + "\n");
                            logArea.setCaretPosition(logArea.getDocument().getLength());
                        });
                    } 
                    else if (line.equals("YOUR_TURN")) {
                        SwingUtilities.invokeLater(() -> {
                            rollButton.setEnabled(true);
                            rollButton.setBackground(new Color(200, 255, 200));
                            infoLabel.setText("★ あなたの番です！ボタンを押してサイコロを振ってください！");
                        });
                    } 
                    else if (line.equals("END")) {
                        SwingUtilities.invokeLater(() -> {
                            rollButton.setEnabled(false);
                            rollButton.setBackground(null);
                            infoLabel.setText("🏁 ゲーム終了 🏁");
                        });
                        break;
                    }
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> logArea.append("サーバーとの通信が切断されました。\n"));
            } finally {
                try { socket.close(); } catch (IOException e) {}
            }
        }
    }

    private class BoardPanel extends JPanel {
        public BoardPanel() {
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (board == null) return;

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int totalMass = board.getLength();
            int cols = (int) Math.ceil((double) totalMass / 2.0);
            if (cols < 1) cols = 1;

            int boxWidth = getWidth() / cols;
            int boxHeight = getHeight() / 2; 

            for (int i = 0; i < totalMass; i++) {
                int row = i / cols;
                int col = i % cols;

                int x = col * boxWidth;
                int y = row * boxHeight;

                int effect = board.getEffect(i);

                if (i == 0) {
                    g2.setColor(new Color(220, 245, 220));
                } else if (i == totalMass - 1) {
                    g2.setColor(new Color(255, 220, 220));
                } else if (effect > 0 && effect < 100) {
                    g2.setColor(new Color(225, 240, 255));
                } else if (effect == 100) {
                    g2.setColor(Color.YELLOW);
                } else if (effect == 101) {
                    g2.setColor(Color.PINK);
                } else if (effect == 102) {
                    g2.setColor(Color.CYAN);
                } else if (effect == 103) {
                    g2.setColor(Color.ORANGE);
                } else if (effect < 0) {
                    g2.setColor(new Color(255, 235, 215));
                } else {
                    g2.setColor(Color.WHITE);
                }

                g2.fillRect(x + 6, y + 6, boxWidth - 12, boxHeight - 12);
                
                g2.setColor(Color.GRAY);
                g2.setStroke(new BasicStroke(2));
                g2.drawRect(x + 6, y + 6, boxWidth - 12, boxHeight - 12);

                g2.setColor(Color.BLACK);
                g2.setFont(new Font("Arial", Font.BOLD, 13));
                String labelStr = (i == 0) ? "START" : (i == totalMass - 1) ? "GOAL" : "マス " + i;
                g2.drawString(labelStr, x + 12, y + 26);

                if (effect != 0) {
                    g2.setFont(new Font("Arial", Font.BOLD, 15));
                    if (effect > 0 && effect < 100) {
                        g2.setColor(new Color(0,100,230));
                        g2.drawString("+" + effect, x + 12, y + 55);
                    } else if (effect < 0) {
                        g2.setColor(new Color(220,30,30));
                        g2.drawString("" + effect, x + 12, y + 55);
                    } else {
                        g2.setColor(Color.BLACK);
                        switch(effect){
                            case 100:
                                g2.drawString("もう1回", x + 12, y + 55);
                                break;
                            case 101:
                                g2.drawString("休み", x + 12, y + 55);
                                break;
                            case 102:
                                g2.drawString("START", x + 12, y + 55);
                                break;
                            case 103:
                                g2.drawString("ワープ", x + 12, y + 55);
                                break;
                        }
                    }
                }

                int pSize = 24; 
                int pY = y + boxHeight - 40; 

                int p1CurrentMas = p1Animating ? Math.round(p1AnimPos) : p1Pos;
                int p2CurrentMas = p2Animating ? Math.round(p2AnimPos) : p2Pos;

                float p1LiftAmount = 0;
                float p2LiftAmount = 0;
                
                if (p1Animating) {
                    float progress = (p1AnimPos - (int)p1AnimPos);
                    p1LiftAmount = (float) Math.sin(progress * Math.PI) * 12;
                }
                
                if (p2Animating) {
                    float progress = (p2AnimPos - (int)p2AnimPos);
                    p2LiftAmount = (float) Math.sin(progress * Math.PI) * 12;
                }

                if (p1CurrentMas == i) {
                    g2.setColor(new Color(50, 110, 240)); 
                    g2.fillOval(x + 15, (int)(pY - p1LiftAmount), pSize, pSize);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.BOLD, 11));
                    g2.drawString("P1", x + 20, (int)(pY + 16 - p1LiftAmount));
                }
                if (p2CurrentMas == i) {
                    g2.setColor(new Color(240, 50, 50)); 
                    int pX = (p1CurrentMas == i && p1Pos == p2Pos) ? x + 44 : x + 15;
                    g2.fillOval(pX, (int)(pY - p2LiftAmount), pSize, pSize);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.BOLD, 11));
                    g2.drawString("P2", pX + 5, (int)(pY + 16 - p2LiftAmount));
                }
            }
            
            // サイコロの表示
            int diceX = this.getWidth() - 120;
            int diceY = 10;
            int diceSize = 100;
            
            Color bgColor = diceAnimating ? new Color(50, 50, 50, 150) : new Color(0, 0, 0, 100);
            g2.setColor(bgColor);
            g2.fillRoundRect(diceX, diceY, diceSize, diceSize, 10, 10);
            
            g2.setColor(diceAnimating ? new Color(255, 200, 0) : Color.BLACK);
            g2.setStroke(new BasicStroke(diceAnimating ? 4 : 3));
            g2.drawRoundRect(diceX, diceY, diceSize, diceSize, 10, 10);
            
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Arial", Font.BOLD, 60));
            String diceText = String.valueOf(diceCurrentValue);
            FontMetrics fm = g2.getFontMetrics();
            int textX = diceX + (diceSize - fm.stringWidth(diceText)) / 2;
            int textY = diceY + ((diceSize - fm.getAscent() + fm.getDescent()) / 2) + fm.getAscent();
            
            if (diceAnimating) {
                g2.setColor(new Color(255, 255, (int)(Math.sin(diceAnimCounter * 0.3) * 100 + 155)));
            }
            g2.drawString(diceText, textX, textY);
            
            if (diceAnimating) {
                g2.setColor(new Color(255, 200, 0));
                g2.setFont(new Font("Arial", Font.BOLD, 13));
                g2.drawString("🎲 回転中！", diceX + 5, diceY + diceSize + 18);
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("使用法: java SugorokuClient <IPアドレス> <ポート番号>");
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        SwingUtilities.invokeLater(() -> {
            try {
                SugorokuClient client = new SugorokuClient(host, port);
                client.setVisible(true);
            } catch (IOException e) {
                System.out.println("サーバーへの接続に失敗しました。");
            }
        });
    }
}
