//v2.4

import java.io.*;
import java.net.Socket;

public class Player {
    private int id;
    private String name;
    private int x = 0;
    private int y = 0;
    private int money = 10000;
    private int items = 0;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connected = true;

    public Player(int id, Socket socket) throws IOException {
        this.id = id;
        this.name = "社長" + id;
        setupStreams(socket);
    }

    // 再接続用のメソッド
    public void reconnect(Socket newSocket) throws IOException {
        setupStreams(newSocket);
        this.connected = true;
    }

    private void setupStreams(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getX() { return x; }
    public void setX(int x) { this.x = x; }
    public int getY() { return y; }
    public void setY(int y) { this.y = y; }
    public int getMoney() { return money; }
    public void addMoney(int amount) { this.money += amount; }
    public boolean isInDebt() { return money < 0; }
    public int getItems() { return items; }
    public void addItems(int amount) { this.items += amount; }
    
    public boolean isConnected() { return connected; }

    public synchronized void sendMessage(String msg) {
        if (connected && out != null) {
            out.println(msg);
        }
    }

    public String receiveMessage() {
        if (!connected) return null;
        try {
            String msg = in.readLine();
            if (msg == null) {
                connected = false; // クライアント側から切断された
            }
            return msg;
        } catch (IOException e) {
            connected = false; // 通信エラーによる切断
            return null;
        }
    }

    public void close() { 
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException e) {} 
    }
}