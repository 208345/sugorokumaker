# sugorokumaker

Javaで動く、通信対応のシンプルな2人用すごろくです。
TCP通信でサーバーと2つのクライアントを接続して遊べます。

## 使い方

```bash
cd /home/runner/work/sugorokumaker/sugorokumaker/208345/sugorokumaker/game
mvn test
mvn package
```

### 1) サーバー起動

```bash
java -cp target/game-1.0-SNAPSHOT.jar jp.sugoroku.SugorokuServer 5000
```

### 2) クライアント起動（2つ）

```bash
java -cp target/game-1.0-SNAPSHOT.jar jp.sugoroku.SugorokuClient 127.0.0.1 5000 Alice
java -cp target/game-1.0-SNAPSHOT.jar jp.sugoroku.SugorokuClient 127.0.0.1 5000 Bob
```

クライアントは自分のターンでEnterを押すとサイコロを振ります。
先にゴール（20マス）に到達したプレイヤーの勝利です。
