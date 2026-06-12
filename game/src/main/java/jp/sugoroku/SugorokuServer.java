package jp.sugoroku;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SugorokuServer {
    private static final int DEFAULT_PORT = 5000;
    private static final int GOAL = 20;

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("サーバー起動: port=" + port);
            List<ClientConnection> clients = new ArrayList<>();
            for (int i = 1; i <= 2; i++) {
                Socket socket = serverSocket.accept();
                ClientConnection client = new ClientConnection(socket);
                client.send("PROMPT_NAME");
                String requestedName = client.read();
                String name = (requestedName == null || requestedName.isBlank()) ? "Player" + i : requestedName.strip();
                client.playerName = name;
                client.send("WELCOME " + name);
                clients.add(client);
                System.out.println(name + " が接続しました");
            }

            SugorokuGame game = SugorokuGame.forTwoPlayers(clients.get(0).playerName, clients.get(1).playerName, GOAL);
            broadcast(clients, "START GOAL=" + GOAL);
            broadcast(clients, formatPositions(game.getPositions()));

            while (!game.isFinished()) {
                String currentPlayer = game.getCurrentPlayer();
                for (ClientConnection client : clients) {
                    if (client.playerName.equals(currentPlayer)) {
                        client.send("YOUR_TURN");
                    } else {
                        client.send("WAIT_TURN " + currentPlayer);
                    }
                }

                ClientConnection activeClient = clients.stream()
                        .filter(c -> c.playerName.equals(currentPlayer))
                        .findFirst()
                        .orElseThrow();

                String command = activeClient.read();
                if (command == null) {
                    broadcast(clients, "GAME_ABORTED " + currentPlayer + " disconnected");
                    break;
                }
                if (!"ROLL".equalsIgnoreCase(command.strip())) {
                    activeClient.send("ERROR UNKNOWN_COMMAND use ROLL");
                    continue;
                }

                SugorokuGame.TurnResult result = game.playTurn();
                broadcast(clients, "RESULT " + result.player() + " rolled=" + result.roll() + " position=" + result.position());
                broadcast(clients, formatPositions(game.getPositions()));
                if (result.finished()) {
                    broadcast(clients, "GAME_OVER winner=" + result.player());
                }
            }

            for (ClientConnection client : clients) {
                client.close();
            }
        }
    }

    private static String formatPositions(Map<String, Integer> positions) {
        return "POSITIONS " + positions.entrySet()
                .stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    private static void broadcast(List<ClientConnection> clients, String message) {
        for (ClientConnection client : clients) {
            client.send(message);
        }
        System.out.println("[送信] " + message);
    }

    private static class ClientConnection {
        private final Socket socket;
        private final BufferedReader reader;
        private final BufferedWriter writer;
        private String playerName;

        private ClientConnection(Socket socket) throws IOException {
            this.socket = socket;
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        private String read() throws IOException {
            return reader.readLine();
        }

        private void send(String message) {
            try {
                writer.write(message);
                writer.newLine();
                writer.flush();
            } catch (IOException ignored) {
            }
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
