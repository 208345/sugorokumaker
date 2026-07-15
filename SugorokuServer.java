//v2.4

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.Point;

// 新概念：物件データクラス
class Property {
    int id;
    int x, y;
    String name;
    int price;
    double rate; // 収益率
    int ownerId = 0; // 0なら未所有

    public Property(int id, int x, int y, String name, int price, double rate) {
        this.id = id; this.x = x; this.y = y; this.name = name; this.price = price; this.rate = rate;
    }
}

public class SugorokuServer {
    private static final int DEFAULT_PLAYERS = 2;
    private static final int DEFAULT_TURN_LIMIT = 20;
    private static final double DEBT_INTEREST_RATE = 0.1;
    private static final double POVERTY_STEAL_RATE = 0.3;

    private static List<Player> players = new ArrayList<>();
    private static List<Property> properties = new ArrayList<>();
    private static Board board;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        int maxPlayers = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PLAYERS;
        int turnLimit = args.length >= 3 && !args[2].toLowerCase().endsWith(".csv") ? Integer.parseInt(args[2]) : DEFAULT_TURN_LIMIT;
        String boardFile = null;
        for (String a : args) if (a.toLowerCase().endsWith(".csv")) boardFile = a;

        if (boardFile != null) {
            try { board = new Board(boardFile); System.out.println("盤面読込成功: " + boardFile); } 
            catch (IOException e) { board = new Board(10, 10); }
        } else { board = new Board(10, 10); }

        generateProperties(); // 盤面の緑マスに物件を配置

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("サーバー起動 (ポート:" + port + ") プレイヤー" + maxPlayers + "人待機中...");

        for (int i = 1; i <= maxPlayers; i++) {
            players.add(new Player(i, serverSocket.accept()));
            players.get(i-1).sendMessage("MSG サーバーに接続しました。");
        }

        // 🌟 切断されたプレイヤーが戻ってきた時のための「再接続受付スレッド」を裏で回す
        new Thread(() -> {
            while (true) {
                try {
                    Socket s = serverSocket.accept();
                    Player reconnector = null;
                    for (Player p : players) {
                        if (!p.isConnected()) { reconnector = p; break; } // 空き（切断中）を探す
                    }
                    if (reconnector != null) {
                        reconnector.reconnect(s);
                        sendBoardInitToPlayer(reconnector); // 盤面情報を再送信して復帰させる
                        broadcastState(players, 1, board);
                        reconnector.sendMessage("MSG === ゲームに復帰しました！ ===");
                        System.out.println(reconnector.getName() + " が再接続しました。");
                    } else {
                        s.close(); // 空き枠がない場合は弾く
                    }
                } catch (Exception e) {}
            }
        }).start();

        for (Player p : players) sendBoardInitToPlayer(p);

        broadcast(players, "MSG 対戦スタート！目的地を目指せ！ (全 " + turnLimit + " 期で決算)");
        Random random = new Random();

        for (int turn = 1; turn <= turnLimit; turn++) {
            broadcast(players, "MSG \n--- 【第 " + turn + " 期】 ---");

            // 🏢 1年（4期）ごとの収益決算
            if (turn > 1 && (turn - 1) % 4 == 0) {
                broadcast(players, "MSG === 🏢 【決算】物件収益の配当 === ");
                for (Player p : players) {
                    int totalRev = 0;
                    for (Property prop : properties) {
                        if (prop.ownerId == p.getId()) totalRev += (int)(prop.price * prop.rate);
                    }
                    if (totalRev > 0) {
                        p.addMoney(totalRev);
                        broadcast(players, "MSG 💰 " + p.getName() + " は物件収益 " + totalRev + "円 を獲得！");
                    }
                }
            }

            for (Player current : players) {
                if (!current.isConnected()) {
                    broadcast(players, "MSG ⚠️ " + current.getName() + " は通信切断中のため、ターンをスキップします。");
                    continue;
                }

                broadcastState(players, turn, board);
                broadcast(players, "MSG ▶ " + current.getName() + " の番です。");

                if (current.isInDebt()) {
                    int interest = (int) Math.ceil(-current.getMoney() * DEBT_INTEREST_RATE);
                    current.addMoney(-interest);
                    broadcast(players, "MSG 💸 " + current.getName() + " は借金中！ 利息 " + interest + "円が加算された...");
                }

                current.sendMessage("YOUR_TURN");
                String cmd = current.receiveMessage();
                if (cmd == null) continue; // ターン中に切断されたらスキップ

                int dice = 0;
                // 【変更点】借金中でも特急カードを使えるように条件 `&& !current.isInDebt()` を廃止
                if (cmd.equals("ITEM") && current.getItems() > 0 /* && !current.isInDebt() */) {
                    current.addItems(-1);
                    dice = random.nextInt(6) + 1 + random.nextInt(6) + 1;
                    broadcast(players, "MSG " + current.getName() + " は【特急カード】を使った！");
                } else {
                    dice = random.nextInt(6) + 1;
                }
                broadcast(players, "DICE " + dice);

                // 🌟 スタック構造を利用した正確な移動・歩数カウント（戻り対応）
                int steps = dice;
                Stack<Point> path = new Stack<>();
                path.push(new Point(current.getX(), current.getY())); // スタート地点を記録

                while (steps > 0) {
                    current.sendMessage("CHOOSE_DIR " + steps);
                    String dir = current.receiveMessage();
                    if (dir == null) break;

                    int nx = current.getX(), ny = current.getY();
                    if (dir.equals("UP")) ny--;
                    else if (dir.equals("DOWN")) ny++;
                    else if (dir.equals("LEFT")) nx--;
                    else if (dir.equals("RIGHT")) nx++;

                    if (nx >= 0 && nx < board.getWidth() && ny >= 0 && ny < board.getHeight()) {
                        // 【変更点】直前のマスに戻った場合はスタックから取り出し、歩数を回復させる
                        if (path.size() >= 2 && path.get(path.size() - 2).x == nx && path.get(path.size() - 2).y == ny) {
                            path.pop(); // 戻った！
                            current.setX(nx); current.setY(ny);
                            steps++;
                        } else {
                            path.push(new Point(nx, ny)); // 新しい道へ
                            current.setX(nx); current.setY(ny);
                            steps--;
                        }
                        broadcastState(players, turn, board);
                    } else {
                        current.sendMessage("MSG ⚠️ そちらには進めません！");
                    }
                }

                if (!current.isConnected()) continue;

                // マスの効果処理
                if (current.getX() == board.getGoalX() && current.getY() == board.getGoalY()) {
                    broadcast(players, "MSG 🎉🎉 " + current.getName() + " が目的地に【ピッタリ】到着！ 援助金 10000円獲得！ 🎉🎉");
                    current.addMoney(10000);
                    board.relocateGoal();
                } else {
                    int tile = board.getTile(current.getX(), current.getY());
                    if (tile == 1) {
                        int gain = (random.nextInt(5) + 1) * 1000; current.addMoney(gain);
                        broadcast(players, "MSG 🔵 青マス: " + gain + "円 獲得！");
                    } else if (tile == 2) {
                        int loss = (random.nextInt(5) + 1) * 1000; current.addMoney(-loss);
                        broadcast(players, "MSG 🔴 赤マス: " + loss + "円 失った...");
                    } else if (tile == 3) {
                        current.addItems(1); broadcast(players, "MSG 🟡 黄マス: 【特急カード】を拾った！");
                    } else if (tile == 4) {
                        applyPovertyGod(players, current, random);
                    } else if (tile == 5) {
                        handlePropertyBuy(current); // 物件の購入処理
                    }
                }

                // 🏢 借金状態なら物件を売却させる
                handleDebtSelling(current);

                broadcastState(players, turn, board);
                try { Thread.sleep(1000); } catch(InterruptedException e){}
            }
        }

        announceRanking(players);
    }

    // 盤面上のすべての緑マスに対して、2つの架空物件を生成する
    private static void generateProperties() {
        int propId = 1;
        Random r = new Random(123); // 毎回同じ物件構成にするためのシード
        String[] types = {"ラーメン屋", "デパート", "遊園地", "IT企業", "タピオカ屋", "謎解き施設", "大学キャンパス"};
        
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y) == 5) {
                    for (int i = 0; i < 2; i++) { // 各駅に2つずつ
                        String name = board.getStationName(x, y) + types[r.nextInt(types.length)];
                        int price = (r.nextInt(10) + 1) * 1000; // 1000〜10000円
                        double rate = (r.nextInt(4) + 1) * 0.1; // 10%〜40%
                        properties.add(new Property(propId++, x, y, name, price, rate));
                    }
                }
            }
        }
    }

    // 物件購入ダイアログのやり取り
    private static void handlePropertyBuy(Player current) {
        List<Property> unowned = new ArrayList<>();
        for (Property p : properties) {
            if (p.x == current.getX() && p.y == current.getY() && p.ownerId == 0) unowned.add(p);
        }
        if (unowned.isEmpty()) {
            broadcast(players, "MSG 🟢 物件マス(" + board.getStationName(current.getX(), current.getY()) + "): 買える物件は売り切れです。");
            return;
        }

        StringBuilder sb = new StringBuilder("ASK_BUY " + board.getStationName(current.getX(), current.getY()) + " ");
        for (Property p : unowned) {
            sb.append(p.id).append(":").append(p.name).append(":").append(p.price).append(":").append(p.rate).append(",");
        }
        current.sendMessage(sb.toString());

        String response = current.receiveMessage();
        if (response != null && response.startsWith("BUY ")) {
            String[] ids = response.substring(4).split(",");
            int totalCost = 0;
            for (String idStr : ids) {
                if(idStr.isEmpty()) continue;
                int pid = Integer.parseInt(idStr);
                for (Property p : unowned) {
                    if (p.id == pid && current.getMoney() >= p.price) {
                        p.ownerId = current.getId();
                        current.addMoney(-p.price);
                        totalCost += p.price;
                        broadcast(players, "MSG 🏢 " + current.getName() + " が【" + p.name + "】(" + p.price + "円) を購入しました！");
                    }
                }
            }
        }
    }

    // 借金時の物件強制売却ループ
    private static void handleDebtSelling(Player current) {
        while (current.getMoney() < 0) {
            List<Property> owned = new ArrayList<>();
            for (Property p : properties) if (p.ownerId == current.getId()) owned.add(p);
            
            if (owned.isEmpty()) break; // 売るものがない

            StringBuilder sb = new StringBuilder("ASK_SELL ");
            for (Property p : owned) sb.append(p.id).append(":").append(p.name).append(":").append(p.price).append(",");
            current.sendMessage(sb.toString());

            String response = current.receiveMessage();
            if (response != null && response.startsWith("SELL ")) {
                try {
                    int pid = Integer.parseInt(response.substring(5));
                    for (Property p : owned) {
                        if (p.id == pid) {
                            p.ownerId = 0; // 手放す
                            current.addMoney(p.price); // フルプライスで売却
                            broadcast(players, "MSG 💥 " + current.getName() + " は借金返済のため【" + p.name + "】を " + p.price + "円 で手放した！");
                            break;
                        }
                    }
                } catch(Exception e) {}
            } else {
                break; // 切断時など
            }
        }
    }

    private static void sendBoardInitToPlayer(Player p) {
        p.sendMessage("INIT " + p.getId());
        StringBuilder bd = new StringBuilder("BOARD_INIT " + board.getWidth() + " " + board.getHeight() + " ");
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                bd.append(board.getTile(x, y)).append(",");
            }
        }
        bd.append(" ");
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if(board.getTile(x, y) == 5) bd.append(x).append(",").append(y).append(",").append(board.getStationName(x, y)).append("|");
            }
        }
        p.sendMessage(bd.toString());
    }

    private static void applyPovertyGod(List<Player> players, Player current, Random random) {
        Player target = null;
        for (Player p : players) {
            if (p == current || !p.isConnected()) continue;
            if (target == null || p.getMoney() > target.getMoney()) target = p;
        }
        if (target == null || target.getMoney() <= 0) {
            broadcast(players, "MSG 🟣 貧乏神が現れたが、奪える相手がいなかった...");
            return;
        }
        int steal = (int) Math.ceil(target.getMoney() * POVERTY_STEAL_RATE);
        target.addMoney(-steal); current.addMoney(steal);
        int tx = target.getX(), ty = target.getY();
        target.setX(current.getX()); target.setY(current.getY());
        current.setX(tx); current.setY(ty);
        broadcast(players, "MSG 🟣 貧乏神が現れた！ " + current.getName() + " は " + target.getName() + " と位置を入れ替え、" + steal + "円 奪った！");
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
        for (int i = 0; i < ranked.size(); i++) broadcast(players, "MSG " + (i+1) + "位: " + ranked.get(i).getName() + " (" + ranked.get(i).getMoney() + "円)");
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