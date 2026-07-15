//v2.3

import java.io.*;
import java.net.*;
import java.util.*;

public class SugorokuServer {
    private static final int DEFAULT_PLAYERS = 2;
    private static final int DEFAULT_TURN_LIMIT = 20;
    private static final double DEBT_INTEREST_RATE = 0.1;   // 借金の利息(10%/ターン)
    private static final double POVERTY_STEAL_RATE = 0.3;   // 貧乏神が奪う割合(30%)

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        int maxPlayers = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PLAYERS;
        int turnLimit = args.length >= 3 && !args[2].toLowerCase().endsWith(".csv")
                ? Integer.parseInt(args[2]) : DEFAULT_TURN_LIMIT;
        String boardFile = null;
        for (String a : args) {
            if (a.toLowerCase().endsWith(".csv")) boardFile = a;
        }

        // 1. 引数にCSVがあれば読み込む
        Board board;
        if (boardFile != null) {
            try {
                board = new Board(boardFile);
                System.out.println("自作盤面を読み込みました: " + boardFile);
            } catch (IOException e) {
                System.out.println("読込失敗。デフォルト盤面で起動します。");
                board = new Board(10, 10);
            }
        } else {
            board = new Board(10, 10);
        }

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("サーバー起動 (ポート:" + port + ") プレイヤー" + maxPlayers + "人待機中...");

        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= maxPlayers; i++) {
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

        broadcast(players, "MSG 対戦スタート！目的地を目指せ！ (全 " + turnLimit + " 期で決算)");
        Random random = new Random();

        for (int turn = 1; turn <= turnLimit; turn++) {
            broadcast(players, "MSG \n--- 【決算ターン " + turn + "】 ---");

            for (Player current : players) {
                broadcastState(players, turn, board);
                broadcast(players, "MSG ▶ " + current.getName() + " の番です。");

                // 借金中は毎ターン利息が発生する
                if (current.isInDebt()) {
                    int interest = (int) Math.ceil(-current.getMoney() * DEBT_INTEREST_RATE);
                    current.addMoney(-interest);
                    broadcast(players, "MSG 💸 " + current.getName() + " は借金中！ 利息 " + interest
                            + "円が加算された...(借金残高 " + (-current.getMoney()) + "円)");
                }

                current.sendMessage("YOUR_TURN");

                String cmd = current.receiveMessage();
                int dice = 0;
                if (cmd.equals("ITEM") && current.getItems() > 0 && !current.isInDebt()) {
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
                    } else if (tile == 4) {
                        applyPovertyGod(players, current, random);
                    } else {
                        broadcast(players, "MSG ⚪ 白マス: 特に何も起きなかった。");
                    }
                }

                broadcastState(players, turn, board);
                try { Thread.sleep(1000); } catch(InterruptedException e){}
            }
        }

        announceRanking(players);
        for (Player p : players) p.close();
    }

    // 🟣 紫マス：貧乏神が現れ、最も裕福な他プレイヤーからお金を奪い、位置を入れ替える
    private static void applyPovertyGod(List<Player> players, Player current, Random random) {
        Player target = null;
        for (Player p : players) {
            if (p == current) continue;
            if (target == null || p.getMoney() > target.getMoney()) target = p;
        }

        if (target == null || target.getMoney() <= 0) {
            broadcast(players, "MSG 🟣 貧乏神が現れたが、奪えるお金を持つ相手はいなかった...");
            return;
        }

        int steal = (int) Math.ceil(target.getMoney() * POVERTY_STEAL_RATE);
        target.addMoney(-steal);
        current.addMoney(steal);

        int tx = target.getX(), ty = target.getY();
        target.setX(current.getX()); target.setY(current.getY());
        current.setX(tx); current.setY(ty);

        broadcast(players, "MSG 🟣 貧乏神が現れた！ " + current.getName() + " は " + target.getName()
                + " から " + steal + "円 奪い、位置を入れ替えた！");
    }

    private static void announceRanking(List<Player> players) {
        List<Player> ranked = new ArrayList<>(players);
        ranked.sort((a, b) -> b.getMoney() - a.getMoney());

        StringBuilder sb = new StringBuilder("GAMEOVER ");
        for (int i = 0; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            if (i > 0) sb.append(",");
            sb.append(p.getName()).append(":").append(p.getMoney());
        }
        broadcast(players, "MSG \n=== 【最終決算】 ===");
        for (int i = 0; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            broadcast(players, "MSG " + (i + 1) + "位: " + p.getName() + " (" + p.getMoney() + "円)");
        }
        broadcast(players, sb.toString());
    }

    private static void broadcast(List<Player> players, String msg) {
        for (Player p : players) p.sendMessage(msg);
    }

    private static void broadcastState(List<Player> players, int turn, Board board) {
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(turn).append(" ").append(players.size());
        for (Player p : players) {
            sb.append(" ").append(p.getX()).append(" ").append(p.getY())
              .append(" ").append(p.getMoney()).append(" ").append(p.getItems())
              .append(" ").append(p.isInDebt() ? 1 : 0);
        }
        sb.append(" ").append(board.getGoalX()).append(" ").append(board.getGoalY());
        broadcast(players, sb.toString());
    }
}
