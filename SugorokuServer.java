// v2.6

import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.Point;

class Property {
    int id, x, y;
    String name;
    int price;
    double rate;
    int ownerId = 0;

    public Property(int id, int x, int y, String name, int price, double rate) {
        this.id = id; this.x = x; this.y = y; this.name = name; this.price = price; this.rate = rate;
    }
}

public class SugorokuServer {
    private static final int DEFAULT_PLAYERS = 2;
    private static final int DEFAULT_YEAR_LIMIT = 5;
    private static final double DEBT_INTEREST_RATE = 0.1;
    private static final double POVERTY_STEAL_RATE = 0.3;

    private static List<Player> players = new ArrayList<>();
    private static List<Property> properties = new ArrayList<>();
    private static Board board;

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(args[0]);
        int maxPlayers = args.length >= 2 ? Integer.parseInt(args[1]) : DEFAULT_PLAYERS;
        
        int yearLimit = args.length >= 3 && !args[2].toLowerCase().endsWith(".csv") ? Integer.parseInt(args[2]) : DEFAULT_YEAR_LIMIT;
        int turnLimit = yearLimit * 4;
        
        String boardFile = null;
        for (String a : args) if (a.toLowerCase().endsWith(".csv")) boardFile = a;

        if (boardFile != null) {
            try { board = new Board(boardFile); System.out.println("盤面読込成功: " + boardFile); } 
            catch (IOException e) { board = new Board(10, 10); }
        } else { board = new Board(10, 10); }

        generateProperties();

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("サーバー起動 (ポート:" + port + ") プレイヤー" + maxPlayers + "人待機中...");

        for (int i = 1; i <= maxPlayers; i++) {
            Player p = new Player(i, serverSocket.accept());
            p.setX(board.getStartX());
            p.setY(board.getStartY());
            players.add(p);
            p.sendMessage("MSG サーバーに接続しました。");
        }

        new Thread(() -> {
            while (true) {
                try {
                    Socket s = serverSocket.accept();
                    Player reconnector = null;
                    for (Player p : players) if (!p.isConnected()) { reconnector = p; break; }
                    
                    if (reconnector != null) {
                        reconnector.reconnect(s);
                        sendBoardInitToPlayer(reconnector);
                        broadcastState(players, 1, board);
                        reconnector.sendMessage("MSG === ゲームに復帰しました！ ===");
                        System.out.println(reconnector.getName() + " が再接続しました。");
                    } else {
                        s.close();
                    }
                } catch (Exception e) {}
            }
        }).start();

        for (Player p : players) sendBoardInitToPlayer(p);

        broadcast(players, "MSG 対戦スタート！目的地を目指せ！ (全 " + yearLimit + " 年で終了)");
        Random random = new Random();
        
        String[] SEASONS = {"春", "夏", "秋", "冬"};

        for (int turn = 1; turn <= turnLimit; turn++) {
            int year = (turn - 1) / 4 + 1;
            String season = SEASONS[(turn - 1) % 4];

            broadcast(players, "MSG \n--- 【" + year + "年" + season + "】 ---");

            if (turn > 1 && (turn - 1) % 4 == 0) {
                broadcast(players, "MSG === 🏢 【" + (year - 1) + "年度 決算】物件収益の配当 === ");
                for (Player p : players) {
                    int totalRev = 0;
                    for (Property prop : properties) if (prop.ownerId == p.getId()) totalRev += (int)(prop.price * prop.rate);
                    if (totalRev > 0) {
                        p.addMoney(totalRev);
                        broadcast(players, "MSG 💰 " + p.getName() + " は物件収益 " + totalRev + "万円 を獲得！");
                    }
                }
            }

            for (Player current : players) {
                if (!current.isConnected()) {
                    broadcast(players, "MSG ⚠️ " + current.getName() + " は通信切断中のため、スキップします。");
                    continue;
                }

                broadcastState(players, turn, board);
                broadcast(players, "MSG ▶ " + current.getName() + " の番です。");

                if (current.isInDebt()) {
                    int interest = (int) Math.ceil(-current.getMoney() * DEBT_INTEREST_RATE);
                    current.addMoney(-interest);
                    broadcast(players, "MSG 💸 " + current.getName() + " は借金中！ 利息 " + interest + "万円が加算された...");
                }

                current.sendMessage("YOUR_TURN");
                String cmd = current.receiveMessage();
                if (cmd == null) continue;

                int diceCount = 1;
                if (cmd.equals("ITEM") && current.getItems() > 0) {
                    current.addItems(-1);
                    diceCount = 2;
                    broadcast(players, "MSG " + current.getName() + " は【特急カード】を使った！");
                }
                
                int[] diceVals = new int[diceCount];
                int totalSteps = 0;
                StringBuilder dicePayload = new StringBuilder("DICE ");
                for (int i = 0; i < diceCount; i++) {
                    diceVals[i] = random.nextInt(6) + 1;
                    totalSteps += diceVals[i];
                    dicePayload.append(diceVals[i]);
                    if (i < diceCount - 1) dicePayload.append(",");
                }
                
                broadcast(players, dicePayload.toString());
                String doneMsg = current.receiveMessage();
                if (doneMsg == null) continue;

                broadcast(players, "MSG 🎲 出目: 【" + totalSteps + "】");

                int steps = totalSteps;
                Stack<Point> path = new Stack<>();
                path.push(new Point(current.getX(), current.getY()));

                while (steps > 0) {
                    current.sendMessage("CHOOSE_DIR " + steps);
                    String dir = current.receiveMessage();
                    if (dir == null) break;

                    int nx = current.getX(), ny = current.getY();
                    if (dir.equals("UP")) ny--;
                    else if (dir.equals("DOWN")) ny++;
                    else if (dir.equals("LEFT")) nx--;
                    else if (dir.equals("RIGHT")) nx++;

                    if (nx >= 0 && nx < board.getWidth() && ny >= 0 && ny < board.getHeight() && board.getTile(nx, ny) != 6) {
                        if (path.size() >= 2 && path.get(path.size() - 2).x == nx && path.get(path.size() - 2).y == ny) {
                            path.pop();
                            current.setX(nx); current.setY(ny);
                            steps++;
                        } else {
                            path.push(new Point(nx, ny));
                            current.setX(nx); current.setY(ny);
                            steps--;
                        }
                        broadcastState(players, turn, board);
                    } else {
                        current.sendMessage("MSG ⚠️ そちらには進めません！");
                    }
                }

                if (!current.isConnected()) continue;

                if (current.getX() == board.getGoalX() && current.getY() == board.getGoalY()) {
                    broadcast(players, "MSG 🎉🎉 " + current.getName() + " が目的地に【ピッタリ】到着！ 援助金 10000万円獲得！ 🎉🎉");
                    current.addMoney(10000);
                    board.relocateGoal();
                    broadcastState(players, turn, board);
                    
                    if (board.getTile(current.getX(), current.getY()) == 5) {
                        handlePropertyBuy(current);
                    }
                } else {
                    int tile = board.getTile(current.getX(), current.getY());
                    if (tile == 1) {
                        int gain = calcEffectAmount(board.getBlueEffect(), random);
                        current.addMoney(gain);
                        broadcast(players, "MSG 🔵 青マス: " + gain + "万円 獲得！");
                    } else if (tile == 2) {
                        int loss = calcEffectAmount(board.getRedEffect(), random);
                        current.addMoney(-loss);
                        broadcast(players, "MSG 🔴 赤マス: " + loss + "万円 失った...");
                    } else if (tile == 3) {
                        current.addItems(1); broadcast(players, "MSG 🟡 黄マス: 【特急カード】を拾った！");
                    } else if (tile == 4) {
                        applyPovertyGod(players, current, random);
                    } else if (tile == 5) {
                        handlePropertyBuy(current);
                    }
                }

                handleDebtSelling(current, turn);
                broadcastState(players, turn, board);
                try { Thread.sleep(1000); } catch(InterruptedException e){}
            }
        }
        
        // 【追加】ゲームループ終了後、最終決算を行う
        broadcast(players, "MSG \n=== 🏢 【最終年度 決算】物件収益の配当 === ");
        for (Player p : players) {
            int totalRev = 0;
            for (Property prop : properties) if (prop.ownerId == p.getId()) totalRev += (int)(prop.price * prop.rate);
            if (totalRev > 0) {
                p.addMoney(totalRev);
                broadcast(players, "MSG 💰 " + p.getName() + " は物件収益 " + totalRev + "万円 を獲得！");
            }
        }
        
        // 最終決算結果を画面に反映させる
        broadcastState(players, turnLimit, board);
        
        announceRanking(players);
    }

    private static int calcEffectAmount(String conf, Random rand) {
        try {
            String[] parts = conf.split("\\|");
            int totalProb = 0;
            for (String p : parts) {
                String[] kv = p.split(",");
                if(kv.length >= 2) totalProb += Integer.parseInt(kv[1].trim());
            }
            if (totalProb == 0) totalProb = 100;

            int r = rand.nextInt(totalProb);
            int sum = 0;
            for (String p : parts) {
                String[] kv = p.split(",");
                if(kv.length < 2) continue;
                int amount = Integer.parseInt(kv[0].trim());
                int prob = Integer.parseInt(kv[1].trim());
                sum += prob;
                if (r < sum) return amount;
            }
            return Integer.parseInt(parts[0].split(",")[0].trim());
        } catch (Exception e) {
            return (rand.nextInt(5) + 1) * 1000;
        }
    }

    private static void updatePlayerAssets() {
        for (Player p : players) {
            int asset = p.getMoney();
            for (Property prop : properties) {
                if (prop.ownerId == p.getId()) asset += prop.price;
            }
            p.setAsset(asset);
        }
    }

    private static void generateProperties() {
        int propId = 1;
        Random r = new Random(123);
        String[] defaultTypes = {"ラーメン屋", "デパート", "遊園地", "IT企業", "タピオカ屋", "謎解き施設", "大学キャンパス"};
        
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if (board.getTile(x, y) == 5) {
                    String fullData = board.getStationData(x, y);
                    if (fullData.contains("|")) {
                        String[] parts = fullData.split("\\|");
                        for (int i = 1; i < parts.length; i++) {
                            String[] pData = parts[i].split(",");
                            if (pData.length >= 3) {
                                properties.add(new Property(propId++, x, y, pData[0].trim(), Integer.parseInt(pData[1].trim()), Double.parseDouble(pData[2].trim())));
                            }
                        }
                    } else {
                        String sName = fullData.isEmpty() ? "名無し駅" : fullData;
                        for (int i = 0; i < 2; i++) {
                            String name = sName + defaultTypes[r.nextInt(defaultTypes.length)];
                            int price = (r.nextInt(10) + 1) * 1000;
                            double rate = (r.nextInt(4) + 1) * 0.1;
                            properties.add(new Property(propId++, x, y, name, price, rate));
                        }
                    }
                }
            }
        }
    }

    private static void handlePropertyBuy(Player current) {
        List<Property> unowned = new ArrayList<>();
        for (Property p : properties) if (p.x == current.getX() && p.y == current.getY() && p.ownerId == 0) unowned.add(p);
        
        if (unowned.isEmpty()) {
            broadcast(players, "MSG 🟢 物件マス(" + board.getStationName(current.getX(), current.getY()) + "): 買える物件は売り切れです。");
            return;
        }

        current.sendMessage(buildAskBuyMsg(current, unowned));

        while (true) {
            String response = current.receiveMessage();
            if (response == null || !response.startsWith("BUY ")) break;
            if (response.equals("BUY NONE")) break;

            String[] ids = response.substring(4).split(",");
            int totalCost = 0;
            List<Property> toBuy = new ArrayList<>();
            for (String idStr : ids) {
                if(idStr.isEmpty()) continue;
                int pid = Integer.parseInt(idStr);
                for (Property p : unowned) if (p.id == pid) { totalCost += p.price; toBuy.add(p); }
            }
            
            if (totalCost > current.getMoney()) {
                current.sendMessage("MSG_ERR お金が足りません！ (所持金:" + current.getMoney() + "万円 / 購入予定額:" + totalCost + "万円)");
                current.sendMessage(buildAskBuyMsg(current, unowned));
                continue;
            }

            for (Property p : toBuy) {
                p.ownerId = current.getId();
                current.addMoney(-p.price);
                broadcast(players, "MSG 🏢 " + current.getName() + " が【" + p.name + "】(" + p.price + "万円) を購入しました！");
            }
            break;
        }
    }

    private static String buildAskBuyMsg(Player current, List<Property> unowned) {
        StringBuilder sb = new StringBuilder("ASK_BUY " + board.getStationName(current.getX(), current.getY()) + " ");
        for (Property p : unowned) {
            sb.append(p.id).append(":").append(p.name).append(":").append(p.price).append(":").append(p.rate).append(",");
        }
        return sb.toString();
    }

    private static void handleDebtSelling(Player current, int turn) {
        while (current.getMoney() < 0) {
            broadcastState(players, turn, board);
            
            List<Property> owned = new ArrayList<>();
            for (Property p : properties) if (p.ownerId == current.getId()) owned.add(p);
            if (owned.isEmpty()) break;

            StringBuilder sb = new StringBuilder("ASK_SELL ");
            for (Property p : owned) sb.append(p.id).append(":").append(p.name).append(":").append(p.price).append(":").append(p.rate).append(",");
            current.sendMessage(sb.toString());

            String response = current.receiveMessage();
            if (response != null && response.startsWith("SELL ")) {
                try {
                    int pid = Integer.parseInt(response.substring(5));
                    for (Property p : owned) {
                        if (p.id == pid) {
                            p.ownerId = 0;
                            current.addMoney(p.price);
                            broadcast(players, "MSG 💥 " + current.getName() + " は借金返済のため【" + p.name + "】を " + p.price + "万円 で手放した！");
                            break;
                        }
                    }
                } catch(Exception e) {}
            } else {
                break;
            }
        }
    }

    private static void sendBoardInitToPlayer(Player p) {
        p.sendMessage("INIT " + p.getId());
        
        StringBuilder bd = new StringBuilder("BOARD_INIT " + board.getWidth() + " " + board.getHeight() + " " + board.getStartX() + " " + board.getStartY() + " ");
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) bd.append(board.getTile(x, y)).append(",");
        }
        bd.append(" ");
        for (int y = 0; y < board.getHeight(); y++) {
            for (int x = 0; x < board.getWidth(); x++) {
                if(board.getTile(x, y) == 5) bd.append(x).append(",").append(y).append(",").append(board.getStationData(x, y)).append("@");
            }
        }
        p.sendMessage(bd.toString());
        
        StringBuilder master = new StringBuilder("PROP_MASTER ");
        for (Property prop : properties) {
            master.append(prop.id).append(":").append(prop.name).append(":")
                  .append(prop.price).append(":").append(prop.rate).append(",");
        }
        p.sendMessage(master.toString());
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
        broadcast(players, "MSG 🟣 貧乏神が現れた！ " + current.getName() + " は " + target.getName() + " と位置を入れ替え、" + steal + "万円 奪った！");
    }

    private static void announceRanking(List<Player> players) {
        updatePlayerAssets(); 
        List<Player> ranked = new ArrayList<>(players);
        ranked.sort((a, b) -> b.getAsset() - a.getAsset());
        
        StringBuilder sb = new StringBuilder("GAMEOVER ");
        for (int i = 0; i < ranked.size(); i++) {
            Player p = ranked.get(i);
            if (i > 0) sb.append(",");
            sb.append(p.getName()).append(":").append(p.getAsset());
        }
        broadcast(players, "MSG \n=== 【最終決算】 ===");
        for (int i = 0; i < ranked.size(); i++) {
            broadcast(players, "MSG " + (i+1) + "位: " + ranked.get(i).getName() + " (総資産 " + ranked.get(i).getAsset() + "万円)");
        }
        broadcast(players, sb.toString());
    }

    private static void broadcast(List<Player> players, String msg) {
        for (Player p : players) p.sendMessage(msg);
    }

    private static void broadcastState(List<Player> players, int turn, Board board) {
        updatePlayerAssets(); 
        
        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(turn).append(" ").append(players.size());
        for (Player p : players) {
            sb.append(" ").append(p.getX()).append(" ").append(p.getY())
              .append(" ").append(p.getMoney()).append(" ").append(p.getItems())
              .append(" ").append(p.isInDebt() ? 1 : 0)
              .append(" ").append(p.getAsset()); 
        }
        sb.append(" ").append(board.getGoalX()).append(" ").append(board.getGoalY());
        broadcast(players, sb.toString());
        
        StringBuilder ps = new StringBuilder("PROP_STATE ");
        for (Property prop : properties) {
            ps.append(prop.id).append(":").append(prop.ownerId).append(",");
        }
        broadcast(players, ps.toString());
    }
}