import java.io.*;
import java.net.Socket;

public class Player {
    private int id;
    private String name;
    private int position;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public Player(int id, Socket socket) throws IOException {
        this.id = id;
        this.name = "プレイヤー" + id;
        this.position = 0;
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
    }

    public String getName() { return name; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }

    // クライアントへメッセージを送信
    public void sendMessage(String msg) {
        out.println(msg);
    }

    // クライアントからのメッセージを受信
    public String receiveMessage() throws IOException {
        return in.readLine();
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // クローズ時の例外は無視
        }
    }
}