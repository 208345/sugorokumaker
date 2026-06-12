# sugorokumaker

Javaのソケット通信を使った複数人すごろくと、盤面を自作できる簡易メーカー機能を追加しました。

## できること
- 複数クライアントでの通信対戦（サーバ/クライアント方式）
- 任意のマス数・イベント内容・移動量で盤面を作成して保存
- 作成した盤面ファイルをサーバ起動時に読み込み

## 使い方

### 1. コンパイル
```bash
javac -d out src/main/java/com/sugorokumaker/SugorokuApp.java
```

### 2. 盤面を作成（任意）
```bash
java -cp out com.sugorokumaker.SugorokuApp maker boards/custom.tsv
```

### 3. サーバ起動
```bash
# デフォルト盤面で2人対戦
java -cp out com.sugorokumaker.SugorokuApp server 5000 2

# 自作盤面を指定
java -cp out com.sugorokumaker.SugorokuApp server 5000 2 boards/custom.tsv
```

### 4. クライアント接続（人数分）
```bash
java -cp out com.sugorokumaker.SugorokuApp client 127.0.0.1 5000
```

接続後、名前を入力し、自分のターンで `ROLL` を送信するとサイコロが振られます。
