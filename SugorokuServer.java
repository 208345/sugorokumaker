import java.io.*;
import java.net.*;
import java.util.*;

public class SugorokuServer {
    private static final int MAX_PLAYERS = 2;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.out.println("使用法: java SugorokuServer <ポート番号> [盤面CSVファイル名]");
            System.exit(1);
        }

        int port = Integer.parseInt(args[0]);
        
        Board board;
        if (args.length >= 2) {
            try {
                board = new Board(args[1]);
                System.out.println("自作盤面ファイルを読み込みました: " + args[1]);
            } catch (IOException e) {
                System.out.println("ファイルの読み込みに失敗したため、デフォルト盤面で起動します: " + e.getMessage());
                board = new Board(20);
            }
        } else {
            board = new Board(20);
            System.out.println("デフォルトの20マス盤面で起動します。");
        }

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("サーバー起動 (ポート:" + port + ")");
        System.out.println(MAX_PLAYERS + "人のプレイヤーを待機中...");

        List<Player> players = new ArrayList<>();
        for (int i = 1; i <= MAX_PLAYERS; i++) {
            Socket socket = serverSocket.accept();
            Player p = new Player(i, socket);
            players.add(p);
            System.out.println(p.getName() + " が接続しました。");
            p.sendMessage("MSG サーバーに接続しました。他のプレイヤーを待っています...");
        }

        // 【修正】各クライアントに自分のIDと、「盤面の構成データ」を送信する
        for (int i = 0; i < players.size(); i++) {
            Player p = players.get(i);
            p.sendMessage("INIT " + (i + 1));
            
            // 盤面データ（マス数と全マスの効果）をカンマ区切りの文字列にして送信
            StringBuilder bd = new StringBuilder("BOARD_INIT " + board.getLength() + " ");
            for (int j = 0; j < board.getLength(); j++) {
                bd.append(board.getEffect(j));
                if (j < board.getLength() - 1) bd.append(",");
            }
            p.sendMessage(bd.toString());
        }

        broadcast(players, "MSG ===================================");
        broadcast(players, "MSG 全員揃いました！すごろく対戦スタート！");
        broadcast(players, "MSG ===================================");

        Random random = new Random();
        int turn = 1;
        boolean isGameOver = false;

        while (!isGameOver) {
            broadcast(players, "MSG \n--- 【ターン " + turn + "】 ---");
            
            for (int i = 0; i < players.size(); i++) {
                Player current = players.get(i);

                // ★追加
                if(current.isSkipTurn()){
                    broadcast(players,
                        "MSG " + current.getName() + " は1回休みです。");

                    current.setSkipTurn(false);
                    continue;
                }

                broadcastState(players, turn, board);
                broadcast(players, "MSG ▶ " + current.getName() + " の番です。");
                current.sendMessage("YOUR_TURN");
                
                try {
                    String cmd = current.receiveMessage();
                    if (cmd == null || !cmd.equals("ROLL")) {
                        isGameOver = true; break;
                    }
                } catch (IOException e) {
                    isGameOver = true; break;
                }

                int dice = random.nextInt(6) + 1;
                broadcast(players, "MSG " + current.getPosition() + " にいる " + current.getName() + " は 【" + dice + "】 の目を出した！");
                
                int newPos = current.getPosition() + dice;
                if (newPos >= board.getLength() - 1) {
                    newPos = board.getLength() - 1;
                }
                current.setPosition(newPos);
                broadcastState(players, turn, board);

                if (checkGoal(players, current, board.getLength())) {
                    isGameOver = true; break;
                }

                int effect = board.getEffect(current.getPosition());

                if(effect != 0){
                
                    broadcast(players,
                        "MSG 止まったマス: "
                        + board.getEffectDescription(current.getPosition()));
                
                    // 普通の進む・戻る
                    if(effect > -100 && effect < 100){
                
                        newPos = current.getPosition() + effect;
                
                        if(newPos < 0)
                            newPos = 0;
                
                        if(newPos >= board.getLength()-1)
                            newPos = board.getLength()-1;
                
                        current.setPosition(newPos);
                    }
                
                    else{
                
                        switch(effect){
                
                            case 100:
                
                                broadcast(players,
                                    "MSG もう一度サイコロを振れます！");
                
                                i--;
                                break;
                
                            case 101:
                
                                current.setSkipTurn(true);
                
                                broadcast(players,
                                    "MSG 次のターンは休みになります。");
                
                                break;
                
                            case 102:

                                current.setPosition(0);
                                
                                broadcast(players,
                                "MSG "+current.getName()+" はスタートへ戻った！");
                
                            case 103:

                                int randomPos = random.nextInt(board.getLength() - 1);
                            
                                current.setPosition(randomPos);
                            
                                broadcast(players,
                                    "MSG " + current.getName() + " はランダムワープした！");
                            
                                break;
                        }
                    }
                
                    try {
                        Thread.sleep(800);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                
                    broadcastState(players, turn, board);
                
                    if(checkGoal(players,current,board.getLength())){
                        isGameOver=true;
                        break;
                    }
                }

                broadcast(players, "MSG " + current.getName() + " は現在 [マス " + current.getPosition() + "] にいます。\n");
                try { Thread.sleep(1000); } catch(InterruptedException e){}
            }
            if (isGameOver) break;
            turn++;
        }

        broadcastState(players, turn, board);
        broadcast(players, "MSG ゲーム終了！お疲れ様でした。");
        broadcast(players, "END");

        for (Player p : players) p.close();
        serverSocket.close();
    }

    private static void broadcast(List<Player> players, String msg) {
        for (Player p : players) p.sendMessage(msg);
    }

    private static void broadcastState(List<Player> players, int turn, Board board) {
        int p1Pos = players.get(0).getPosition();
        int p2Pos = players.get(1).getPosition();
        String stateMsg = "UPDATE " + turn + " " + p1Pos + " " + p2Pos;
        broadcast(players, stateMsg);
    }

    private static boolean checkGoal(List<Player> players, Player player, int boardLength) {
        if (player.getPosition() >= boardLength - 1) {
            broadcast(players, "MSG \n🎉🎉🎉 " + player.getName() + " がゴールに到達しました！ 🎉🎉🎉");
            return true;
        }
        return false;
    }
}
