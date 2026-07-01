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

        new Thread(new ServerReceiver()).start();
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
                    // 【追加】サーバーから送られてきた盤面データを受け取り、再構成する
                    else if (line.startsWith("BOARD_INIT ")) {
                        String[] tokens = line.split(" ");
                        int length = Integer.parseInt(tokens[1]);
                        String[] eff = tokens[2].split(",");
                        
                        // サーバーと同じ状態の盤面をクライアント側にも生成
                        board = new Board(length);
                        for (int i = 0; i < length; i++) {
                            board.setEffect(i, Integer.parseInt(eff[i]));
                        }
                        
                        SwingUtilities.invokeLater(() -> boardPanel.repaint());
                    }
                    else if (line.startsWith("UPDATE ")) {
                        String[] tokens = line.split(" ");
                        currentTurn = Integer.parseInt(tokens[1]);
                        p1Pos = Integer.parseInt(tokens[2]);
                        p2Pos = Integer.parseInt(tokens[3]);
                        
                        SwingUtilities.invokeLater(() -> {
                            infoLabel.setText("【ターン " + currentTurn + "】 あなた: プレイヤー" + myId + "  |  P1: マス" + p1Pos + "  |  P2: マス" + p2Pos);
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

            // 【修正】固定の10列ではなく、サーバーから受け取ったマス数から動的に計算（2段に分ける）
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

                if (p1Pos == i) {
                    g2.setColor(new Color(50, 110, 240)); 
                    g2.fillOval(x + 15, pY, pSize, pSize);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.BOLD, 11));
                    g2.drawString("P1", x + 20, pY + 16);
                }
                if (p2Pos == i) {
                    g2.setColor(new Color(240, 50, 50)); 
                    int pX = (p1Pos == i) ? x + 44 : x + 15;
                    g2.fillOval(pX, pY, pSize, pSize);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("Arial", Font.BOLD, 11));
                    g2.drawString("P2", pX + 5, pY + 16);
                }
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
