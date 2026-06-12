package com.sugorokumaker;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public final class SugorokuApp {
    private SugorokuApp() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            return;
        }

        switch (args[0]) {
            case "server" -> runServer(args);
            case "client" -> runClient(args);
            case "maker" -> runMaker(args);
            default -> printUsage();
        }
    }

    private static void runServer(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.out.println("Usage: server <port> <playerCount> [boardFile]");
            return;
        }

        int port = Integer.parseInt(args[1]);
        int playerCount = Integer.parseInt(args[2]);
        if (playerCount < 2) {
            throw new IllegalArgumentException("playerCount must be 2 or more");
        }

        Board board = args.length == 4 ? Board.load(Path.of(args[3])) : Board.defaultBoard();
        GameServer server = new GameServer(port, playerCount, board);
        server.start();
    }

    private static void runClient(String[] args) throws Exception {
        if (args.length != 3) {
            System.out.println("Usage: client <host> <port>");
            return;
        }

        String host = args[1];
        int port = Integer.parseInt(args[2]);

        try (Socket socket = new Socket(host, port);
             BufferedReader socketIn = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter socketOut = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = socketIn.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (IOException ignored) {
                }
            });
            reader.setDaemon(true);
            reader.start();

            String input;
            while ((input = stdin.readLine()) != null) {
                socketOut.write(input);
                socketOut.newLine();
                socketOut.flush();
            }
        }
    }

    private static void runMaker(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: maker <outputBoardFile>");
            return;
        }

        Path output = Path.of(args[1]);
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        System.out.print("マス数を入力してください(2以上): ");
        int size = Integer.parseInt(stdin.readLine());
        if (size < 2) {
            throw new IllegalArgumentException("マス数は2以上にしてください");
        }

        List<Square> squares = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            System.out.printf("%dマス目のイベント説明(空文字可): ", i);
            String message = stdin.readLine();
            if (message == null) {
                message = "";
            }

            System.out.printf("%dマス目の移動量(-6〜6, 0で変化なし): ", i);
            int moveDelta = Integer.parseInt(stdin.readLine());
            squares.add(new Square(moveDelta, message));
        }

        Board board = new Board(squares);
        board.save(output);
        System.out.println("盤面を保存しました: " + output.toAbsolutePath());
    }

    private static void printUsage() {
        System.out.println("SugorokuApp usage:");
        System.out.println("  server <port> <playerCount> [boardFile]");
        System.out.println("  client <host> <port>");
        System.out.println("  maker <outputBoardFile>");
    }

    private static final class GameServer {
        private final int port;
        private final int playerCount;
        private final Board board;
        private final Random random = new Random();

        private GameServer(int port, int playerCount, Board board) {
            this.port = port;
            this.playerCount = playerCount;
            this.board = board;
        }

        private void start() throws Exception {
            List<ClientHandler> players = new ArrayList<>();
            Map<ClientHandler, Integer> positions = new HashMap<>();

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                System.out.println("サーバ起動: port=" + port + " プレイヤー待機中...");

                for (int i = 1; i <= playerCount; i++) {
                    Socket socket = serverSocket.accept();
                    ClientHandler client = new ClientHandler(socket, i);
                    client.send("WELCOME プレイヤー" + i + "。名前を入力してください:");
                    String name = client.readLine();
                    if (name == null || name.isBlank()) {
                        name = "Player" + i;
                    }
                    client.setName(name.trim());
                    players.add(client);
                    positions.put(client, 0);
                    broadcast(players, "JOIN " + client.getName() + " が参加しました");
                }

                broadcast(players, "GAME_START 全" + board.size() + "マス");
                int turn = 0;

                while (true) {
                    ClientHandler current = players.get(turn % players.size());
                    Integer currentPos = positions.get(current);
                    if (currentPos == null) {
                        turn++;
                        continue;
                    }

                    current.send("YOUR_TURN ROLL と入力してください");
                    String command = current.readLine();
                    if (command == null) {
                        broadcast(players, current.getName() + " が切断しました");
                        positions.remove(current);
                        current.closeQuietly();
                        if (positions.size() <= 1) {
                            break;
                        }
                        turn++;
                        continue;
                    }

                    if (!"ROLL".equalsIgnoreCase(command.trim())) {
                        current.send("ERROR ROLL を入力してください");
                        continue;
                    }

                    int dice = random.nextInt(6) + 1;
                    int moved = Math.min(board.lastIndex(), currentPos + dice);
                    Square landed = board.get(moved);
                    int afterEvent = Math.max(0, Math.min(board.lastIndex(), moved + landed.moveDelta()));
                    positions.put(current, afterEvent);

                    String eventText = landed.message().isBlank() ? "イベントなし" : landed.message();
                    broadcast(players,
                            String.format("TURN %s dice=%d pos=%d eventMove=%d event=%s final=%d",
                                    current.getName(), dice, moved, landed.moveDelta(), eventText, afterEvent));

                    if (afterEvent >= board.lastIndex()) {
                        broadcast(players, "WINNER " + current.getName());
                        break;
                    }
                    turn++;
                }
            } finally {
                for (ClientHandler player : players) {
                    player.closeQuietly();
                }
            }
        }

        private static void broadcast(List<ClientHandler> clients, String message) {
            for (ClientHandler client : clients) {
                client.send(message);
            }
        }
    }

    private static final class ClientHandler {
        private final Socket socket;
        private final BufferedReader in;
        private final BufferedWriter out;
        private String name;

        private ClientHandler(Socket socket, int id) throws IOException {
            this.socket = socket;
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            this.name = "Player" + id;
        }

        private void setName(String name) {
            this.name = Objects.requireNonNullElse(name, this.name);
        }

        private String getName() {
            return name;
        }

        private String readLine() throws IOException {
            return in.readLine();
        }

        private synchronized void send(String message) {
            try {
                out.write(message);
                out.newLine();
                out.flush();
            } catch (IOException ignored) {
            }
        }

        private void closeQuietly() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private record Square(int moveDelta, String message) {
    }

    private record Board(List<Square> squares) {
        private Board {
            if (squares == null || squares.size() < 2) {
                throw new IllegalArgumentException("盤面は2マス以上必要です");
            }
            squares = List.copyOf(squares);
        }

        private int size() {
            return squares.size();
        }

        private int lastIndex() {
            return squares.size() - 1;
        }

        private Square get(int index) {
            return squares.get(index);
        }

        private void save(Path path) throws IOException {
            List<String> lines = new ArrayList<>();
            lines.add("SIZE\t" + squares.size());
            for (int i = 0; i < squares.size(); i++) {
                Square sq = squares.get(i);
                String sanitized = sq.message().replace("\t", " ").replace("\n", " ");
                lines.add(i + "\t" + sq.moveDelta() + "\t" + sanitized);
            }
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(path, lines, StandardCharsets.UTF_8);
        }

        private static Board load(Path path) throws IOException {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            if (lines.isEmpty() || !lines.get(0).startsWith("SIZE\t")) {
                throw new IllegalArgumentException("盤面ファイルの形式が不正です");
            }

            int size = Integer.parseInt(lines.get(0).substring("SIZE\t".length()).trim());
            List<Square> squares = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                squares.add(new Square(0, ""));
            }

            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split("\t", 3);
                if (parts.length < 3) {
                    continue;
                }
                int index = Integer.parseInt(parts[0]);
                int delta = Integer.parseInt(parts[1]);
                String message = parts[2];
                if (index >= 0 && index < size) {
                    squares.set(index, new Square(delta, message));
                }
            }
            return new Board(squares);
        }

        private static Board defaultBoard() {
            List<Square> squares = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                squares.add(new Square(0, ""));
            }
            squares.set(3, new Square(2, "はしごを見つけた! 2マス進む"));
            squares.set(8, new Square(-2, "落とし穴! 2マス戻る"));
            squares.set(14, new Square(3, "追い風! 3マス進む"));
            squares.set(20, new Square(-3, "寄り道した! 3マス戻る"));
            squares.set(25, new Square(2, "ゴールが見えた! 2マス進む"));
            return new Board(squares);
        }
    }
}
