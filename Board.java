import java.io.*;

public class Board {
    private int length;
    private int[] effects;

    // デフォルト20マス用のコンストラクタ
    public Board(int length) {
        this.length = length;
        this.effects = new int[length];
        
        if (length >= 20) {
            effects[4] = 2;   // 4マス目: 2マス進む
            effects[9] = -3;  // 9マス目: 3マス戻る
            effects[14] = 3;  // 14マス目: 3マス進む
            effects[17] = -2; // 17マス目: 2マス戻る
        }
    }

    // CSVファイルから盤面を読み込むコンストラクタ
    public Board(String filename) throws IOException {
        loadFromFile(filename);
    }

    public int getLength() {
        return length;
    }

    // 【追加】盤面のサイズを動的に変更するメソッド
    public void resize(int newLength) {
        if (newLength < 2) {
            newLength = 2; // 最低でもSTARTとGOALの2マスは必要
        }
        
        int[] newEffects = new int[newLength];
        // 既存の効果を新しい配列にコピー（サイズが小さくなった場合は溢れた分は切り捨て）
        int copyLength = Math.min(this.length, newLength);
        System.arraycopy(this.effects, 0, newEffects, 0, copyLength);
        
        // ゴール位置が変わるため、古いゴール地点にあった効果はリセットする
        if (this.length < newLength) {
            newEffects[this.length - 1] = 0; 
        }
        // 新しいゴール地点の効果も0にする
        newEffects[newLength - 1] = 0;

        this.length = newLength;
        this.effects = newEffects;
    }

    public int getEffect(int position) {
        if (position >= 0 && position < length) {
            return effects[position];
        }
        return 0;
    }

    public void setEffect(int position, int effect) {
        if (position >= 0 && position < length) {
            this.effects[position] = effect;
        }
    }

    //v1.1で追加した
    public String getEffectDescription(int position) {

    int effect = getEffect(position);

    if(effect > 0 && effect < 100)
        return effect + "マス進む！";

    if(effect < 0)
        return Math.abs(effect) + "マス戻る！";

    switch(effect){

        case 100:
            return "もう一回サイコロ！";

        case 101:
            return "次のターン休み！";

        case 102:
            return "スタートへ戻る！";

        case 103:
            return "ランダムワープ！";

        default:
            return "特に何もない。";
        }
    }

    // 盤面データをCSVファイルに保存
    public void saveToFile(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            pw.println(length);
            for (int i = 0; i < length; i++) {
                pw.println(i + "," + effects[i]);
            }
        }
    }

    // CSVファイルから盤面データを読み込み
    public void loadFromFile(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();
            if (line == null) throw new IOException("ファイルが空です。");
            this.length = Integer.parseInt(line.trim());
            this.effects = new int[length];

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split(",");
                int index = Integer.parseInt(tokens[0]);
                int effect = Integer.parseInt(tokens[1]);
                if (index >= 0 && index < length) {
                    effects[index] = effect;
                }
            }
        }
    }
}
