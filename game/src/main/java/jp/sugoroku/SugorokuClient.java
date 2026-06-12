package jp.sugoroku;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class SugorokuClient {
    private static final int DEFAULT_PORT = 5000;

    public static void main(String[] args) throws IOException {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_PORT;
        String preferredName = args.length > 2 ? args[2] : null;

        try (Socket socket = new Socket(host, port);
                BufferedReader serverIn = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter serverOut = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader console = new BufferedReader(
                        new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = serverIn.readLine()) != null) {
                if ("PROMPT_NAME".equals(line)) {
                    String name = preferredName;
                    if (name == null || name.isBlank()) {
                        System.out.print("プレイヤー名を入力: ");
                        name = console.readLine();
                    }
                    send(serverOut, name == null ? "" : name);
                    continue;
                }

                if ("YOUR_TURN".equals(line)) {
                    System.out.println("あなたの番です。Enterでサイコロを振る");
                    console.readLine();
                    send(serverOut, "ROLL");
                    continue;
                }

                if (line.startsWith("WAIT_TURN ")) {
                    System.out.println("相手のターン: " + line.substring("WAIT_TURN ".length()));
                    continue;
                }

                if (line.startsWith("RESULT ")) {
                    System.out.println(line);
                    continue;
                }

                if (line.startsWith("POSITIONS ")) {
                    System.out.println("現在位置: " + line.substring("POSITIONS ".length()));
                    continue;
                }

                if (line.startsWith("GAME_OVER ") || line.startsWith("GAME_ABORTED ")) {
                    System.out.println(line);
                    break;
                }

                System.out.println(line);
            }
        }
    }

    private static void send(BufferedWriter writer, String message) throws IOException {
        writer.write(message);
        writer.newLine();
        writer.flush();
    }
}
