package jp.sugoroku;

public class App {
    public static void main(String[] args) {
        System.out.println("実行方法:");
        System.out.println(" サーバー: java -cp target/game-1.0-SNAPSHOT.jar jp.sugoroku.SugorokuServer [port]");
        System.out.println(" クライアント: java -cp target/game-1.0-SNAPSHOT.jar jp.sugoroku.SugorokuClient [host] [port] [name]");
    }
}
