//v2.0

import java.io.*;
import java.net.*;
import java.util.*;

public class SugorokuServer {
    private static final int MAX_PLAYERS = 2;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        
        // 1. 引数にCSVがあれば読み込む
        Board board;
        if (args.length >= 2) {
            try {
                board = new Board(args[1]);
                System.out.println("自作盤面を読み込みました: " + args[1]);
            } catch (IOException e) {
                System.out.println("読込失敗。デフォルト盤面で起動します。");
                board = new Board(10, 10);
            }
        } else {
            board = new Board(10, 10);
        }

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("サーバー起動 (ポート:" + port + ") プレイヤー待機中...");

        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= MAX_PLAYERS; i++) {
            Player p = new Player(i, serverSocket.accept());
            players.add(p);
            p.sendMessage("MSG サーバーに接続しました。");
        }

        for (int i = 0; i < players.size(); i++) {
            players.get(i).sendMessage("INIT " + (i + 1));
            StringBuilder bd = new StringBuilder("BOARD_INIT " + board.getWidth() + " " + board.getHeight() + " ");
            for (int y = 0; y < board.getHeight(); y++) {
                for (int x = 0; x < board.getWidth(); x++) {
                    bd.append(board.getTile(x, y)).append(",");
                }
            }
            players.get(i).sendMessage(bd.toString());
        }

        broadcast(players, "MSG 対戦スタート！目的地を目指せ！");
        Random random = new Random();
        int turn = 1;

        while (true) {
            broadcast(players, "MSG \n--- 【決算ターン " + turn + "】 ---");
            
            for (Player current : players) {
                broadcastState(players, turn, board);
                broadcast(players, "MSG ▶ " + current.getName() + " の番です。");
                current.sendMessage("YOUR_TURN");
                
                String cmd = current.receiveMessage();
                int dice = 0;
                if (cmd.equals("ITEM") && current.getItems() > 0) {
                    current.addItems(-1);
                    dice = random.nextInt(6) + 1 + random.nextInt(6) + 1;
                    broadcast(players, "MSG " + current.getName() + " は【特急カード】を使った！");
                } else {
                    dice = random.nextInt(6) + 1;
                }
                broadcast(players,
                    "MSG 🎲 出目: 【"+dice+"】");

                broadcast(players,
                    "DICE "+dice);

                // 2. 移動処理（ここではゴール判定はしない。通過を許容する）
                int steps = dice;
                while (steps > 0) {
                    current.sendMessage("CHOOSE_DIR " + steps);
                    String dir = current.receiveMessage();
                    
                    int nx = current.getX();
                    int ny = current.getY();
                    if (dir.equals("UP")) ny--;
                    else if (dir.equals("DOWN")) ny++;
                    else if (dir.equals("LEFT")) nx--;
                    else if (dir.equals("RIGHT")) nx++;
                    
                    if (nx >= 0 && nx < board.getWidth() && ny >= 0 && ny < board.getHeight()) {
                        current.setX(nx);
                        current.setY(ny);
                        steps--;
                        broadcastState(players, turn, board);
                    } else {
                        // 画面外への進行は無効化（歩数は消費されない）
                        current.sendMessage("MSG ⚠️ そちらには進めません！");
                    }
                }

                // 3. 移動終了後（停止時）のピッタリ判定とマスの効果発動
                if (current.getX() == board.getGoalX() && current.getY() == board.getGoalY()) {
                    broadcast(players, "MSG 🎉🎉 " + current.getName() + " が目的地に【ピッタリ】到着！ 援助金 10000円を獲得！ 🎉🎉");
                    current.addMoney(10000);
                    board.relocateGoal(); // 新しい目的地へ
                } else {
                    int tile = board.getTile(current.getX(), current.getY());
                    if (tile == 1) {
                        int gain = (random.nextInt(5) + 1) * 1000;
                        current.addMoney(gain);
                        broadcast(players, "MSG 🔵 青マス: " + gain + "円 獲得！");
                    } else if (tile == 2) {
                        int loss = (random.nextInt(5) + 1) * 1000;
                        current.addMoney(-loss);
                        broadcast(players, "MSG 🔴 赤マス: " + loss + "円 失った...");
                    } else if (tile == 3) {
                        current.addItems(1);
                        broadcast(players, "MSG 🟡 黄マス: 【特急カード(サイコロ2個)】を拾った！");
                    } else {
                        broadcast(players, "MSG ⚪ 白マス: 特に何も起きなかった。");
                    }
                }
                
                broadcastState(players, turn, board);
                try { Thread.sleep(1000); } catch(InterruptedException e){}
            }
            turn++;
        }
    }

    private static void broadcast(List<Player> players, String msg) {
        for (Player p : players) p.sendMessage(msg);
    }

    private static void broadcastState(List<Player> players, int turn, Board board) {
        Player p1 = players.get(0);
        Player p2 = players.get(1);
        String state = String.format("UPDATE %d %d %d %d %d %d %d %d %d %d %d",
            turn, p1.getX(), p1.getY(), p1.getMoney(), p1.getItems(),
            p2.getX(), p2.getY(), p2.getMoney(), p2.getItems(),
            board.getGoalX(), board.getGoalY());
        broadcast(players, state);
    }
}