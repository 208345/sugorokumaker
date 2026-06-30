import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public class BoardEditor extends JFrame {
    private static final int DEFAULT_LENGTH = 20;
    private Board board;
    private JPanel gridPanel;       // マス目ボタンを載せるパネル（動的に中身を入れ替える）
    private JButton[] massButtons;  // マス目ボタンの配列
    private JLabel statusLabel;

    public BoardEditor() {
        setTitle("すごろく盤面エディタ (マス数可変版)");
        setSize(900, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        board = new Board(DEFAULT_LENGTH); // 初期状態は20マス

        // 上部メニュー（保存・読込・サイズ変更ボタン）
        JPanel menuPanel = new JPanel();
        JButton saveButton = new JButton("💾 盤面をCSV保存");
        JButton loadButton = new JButton("📂 CSVから盤面読込");
        JButton resizeButton = new JButton("⚙ マス数を変更");
        statusLabel = new JLabel("現在のマス数: " + board.getLength());
        
        menuPanel.add(saveButton);
        menuPanel.add(loadButton);
        menuPanel.add(resizeButton);
        menuPanel.add(statusLabel);
        add(menuPanel, BorderLayout.NORTH);

        // 中央：マス目ボタンの配置用パネルを初期化
        gridPanel = new JPanel();
        add(gridPanel, BorderLayout.CENTER);

        // 最初のマス目UIを構築
        buildBoardUI();

        // 【追加】マス数変更ボタンの処理
        resizeButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String input = JOptionPane.showInputDialog(
                    BoardEditor.this,
                    "新しいマスの総数を入力してください（2〜100マスを推奨）:\n※サイズを縮小した場合、溢れたマスの効果は消去されます。",
                    board.getLength()
                );
                
                if (input != null) {
                    try {
                        int newLength = Integer.parseInt(input.trim());
                        if (newLength < 2) {
                            JOptionPane.showMessageDialog(BoardEditor.this, "マス数は2以上にしてくさだい。", "エラー", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        
                        // 1. 内部データのサイズを変更
                        board.resize(newLength);
                        // 2. 画面のボタンUIを再構築
                        buildBoardUI();
                        statusLabel.setText("現在のマス数: " + board.getLength());
                        
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(BoardEditor.this, "有効な数値を入力してください。", "エラー", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        // 保存ボタンの処理
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("盤面データの保存");
                int userSelection = fileChooser.showSaveDialog(BoardEditor.this);
                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    try {
                        String filePath = fileChooser.getSelectedFile().getAbsolutePath();
                        if (!filePath.toLowerCase().endsWith(".csv")) {
                            filePath += ".csv";
                        }
                        board.saveToFile(filePath);
                        JOptionPane.showMessageDialog(BoardEditor.this, "盤面を保存しました:\n" + filePath);
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(BoardEditor.this, "保存に失敗しました: " + ex.getMessage(), "エラー", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        // 読み込みボタンの処理
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser fileChooser = new JFileChooser();
                fileChooser.setDialogTitle("盤面データの読み込み");
                int userSelection = fileChooser.showOpenDialog(BoardEditor.this);
                if (userSelection == JFileChooser.APPROVE_OPTION) {
                    try {
                        String filePath = fileChooser.getSelectedFile().getAbsolutePath();
                        board.loadFromFile(filePath);
                        
                        // ファイル内のマス数に合わせてUIを再構築
                        buildBoardUI();
                        statusLabel.setText("現在のマス数: " + board.getLength());
                        
                        JOptionPane.showMessageDialog(BoardEditor.this, "盤面を読み込みました。");
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(BoardEditor.this, "読み込みに失敗しました。ファイル形式を確認してください。", "エラー", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });
    }

    // 【重要】現在のboardオブジェクトの状態に合わせて、中央のボタン群を動的に組み立てるメソッド
    private void buildBoardUI() {
        // 一度パネルの中身を完全にクリアする
        gridPanel.removeAll();

        int totalMass = board.getLength();
        // マス数をきれいに2段（行）で収めるため、1行あたりの列数を計算
        int cols = (int) Math.ceil((double) totalMass / 2.0);
        if (cols < 1) cols = 1;

        gridPanel.setLayout(new GridLayout(2, cols, 5, 5));
        gridPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        massButtons = new JButton[totalMass];

        for (int i = 0; i < totalMass; i++) {
            final int index = i;
            massButtons[i] = new JButton();
            updateButtonText(index);
            
            // スタート（0）とゴール（最後のマス）以外は編集可能
            if (i > 0 && i < totalMass - 1) {
                massButtons[index].addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        String input = JOptionPane.showInputDialog(
                            BoardEditor.this,
                            "マス " + index + " の効果を入力してください。\n\n"
                            + "0：何もなし\n"
                            + "正の数：○マス進む\n"
                            + "負の数：○マス戻る\n"
                            + "100：もう一回サイコロ\n"
                            + "101：1回休み\n"
                            + "102：スタートへ戻る\n"
                            + "103：ランダムワープ",
                            board.getEffect(index)
                        );
                        
                        if (input != null) {
                            try {
                                int effect = Integer.parseInt(input.trim());
                                board.setEffect(index, effect);
                                updateButtonText(index);
                            } catch (NumberFormatException ex) {
                                JOptionPane.showMessageDialog(BoardEditor.this, "数値を入力してください。", "エラー", JOptionPane.ERROR_MESSAGE);
                            }
                        }
                    }
                });
            } else {
                massButtons[index].setEnabled(false); // START, GOALは固定
            }
            gridPanel.add(massButtons[index]);
        }

        // GUIのレイアウトを再計算し、画面を強制リフレッシュする
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    // 各マスのボタンの色とテキストを更新する
    private void updateButtonText(int index) {
        int effect = board.getEffect(index);
        if (index == 0) {
            massButtons[index].setText("START");
            massButtons[index].setBackground(new Color(220, 245, 220));
        } else if (index == board.getLength() - 1) {
            massButtons[index].setText("GOAL");
            massButtons[index].setBackground(new Color(255, 220, 220));
        } else {
            if(effect > 0 && effect < 100){
                
                massButtons[index].setText(
                    "<html><center>マス "
                    + index
                    + "<br><font color='blue'><b>+"
                    + effect
                    + "</b></font></center></html>"
                );
            
                massButtons[index].setBackground(new Color(225,240,255));
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new BoardEditor().setVisible(true);
        });
    }
}
