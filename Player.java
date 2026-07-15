//v2.3

import java.io.*;
import java.net.Socket;

public class Player {
    private int id;
    private String name;
    private int x = 0;
    private int y = 0;
    private int money = 10000; // 初期資金1万円
    private int items = 0;     // サイコロ2個振れるアイテムの所持数

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public Player(int id, Socket socket) throws IOException {
        this.id = id;
        this.name = "社長" + id; // 桃鉄風に「社長」
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

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

    public void sendMessage(String msg) { out.println(msg); }
    public String receiveMessage() throws IOException { return in.readLine(); }
    public void close() { try { socket.close(); } catch (IOException e) {} }
}
