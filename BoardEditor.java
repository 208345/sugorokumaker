// v2.6

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Arrays;

public class BoardEditor extends JFrame {
    private Board board;
    private JPanel gridPanel;
    private JButton[][] massButtons;
    private JLabel statusLabel;

    public BoardEditor() {
        setTitle("2Dマップエディタ (v2.10版)");
        setSize(900, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        board = new Board(10, 10);

        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        
        JPanel menuPanel1 = new JPanel(new FlowLayout());
        JButton saveButton = new JButton("💾 CSV保存");
        JButton loadButton = new JButton("📂 CSV読込");
        JButton resizeButton = new JButton("⚙ サイズ変更");
        JButton blueConfBtn = new JButton("🔵 青マス設定");
        JButton redConfBtn = new JButton("🔴 赤マス設定");
        JButton startPosBtn = new JButton("🚩 スタート位置設定");
        
        menuPanel1.add(saveButton);
        menuPanel1.add(loadButton);
        menuPanel1.add(resizeButton);
        menuPanel1.add(blueConfBtn);
        menuPanel1.add(redConfBtn);
        menuPanel1.add(startPosBtn);
        
        JPanel menuPanel2 = new JPanel();
        statusLabel = new JLabel("マスをクリックすると、設定する種類を選ぶことができます。");
        menuPanel2.add(statusLabel);
        
        topPanel.add(menuPanel1);
        topPanel.add(menuPanel2);
        add(topPanel, BorderLayout.NORTH);

        gridPanel = new JPanel();
        add(gridPanel, BorderLayout.CENTER);

        buildBoardUI();

        resizeButton.addActionListener(e -> {
            String wStr = JOptionPane.showInputDialog(this, "横のマス数を入力:", board.getWidth());
            String hStr = JOptionPane.showInputDialog(this, "縦のマス数を入力:", board.getHeight());
            if (wStr != null && hStr != null) {
                try {
                    board.resize(Integer.parseInt(wStr.trim()), Integer.parseInt(hStr.trim()));
                    buildBoardUI();
                } catch (NumberFormatException ex) {}
            }
        });

        blueConfBtn.addActionListener(e -> openEffectConfigDialog("青マス(プラス) の金額設定", true));
        redConfBtn.addActionListener(e -> openEffectConfigDialog("赤マス(マイナス) の金額設定", false));

        startPosBtn.addActionListener(e -> {
            while (true) {
                String xStr = JOptionPane.showInputDialog(this, "スタート位置のX座標(0〜" + (board.getWidth() - 1) + ")を入力:", board.getStartX());
                if (xStr == null) break;
                String yStr = JOptionPane.showInputDialog(this, "スタート位置のY座標(0〜" + (board.getHeight() - 1) + ")を入力:", board.getStartY());
                if (yStr == null) break;
                try {
                    int x = Integer.parseInt(xStr.trim());
                    int y = Integer.parseInt(yStr.trim());
                    if (x < 0 || x >= board.getWidth() || y < 0 || y >= board.getHeight()) {
                        JOptionPane.showMessageDialog(this, "盤面からはみ出しています！", "エラー", JOptionPane.ERROR_MESSAGE);
                        continue;
                    }
                    board.setStartPos(x, y);
                    buildBoardUI();
                    JOptionPane.showMessageDialog(this, "スタート位置を (" + x + ", " + y + ") に設定しました。");
                    break;
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "数値を入力してください！", "エラー", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        saveButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    String filePath = fileChooser.getSelectedFile().getAbsolutePath();
                    if (!filePath.toLowerCase().endsWith(".csv")) filePath += ".csv";
                    board.saveToFile(filePath);
                    JOptionPane.showMessageDialog(this, "保存しました:\n" + filePath);
                } catch (IOException ex) {}
            }
        });

        loadButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    board.loadFromFile(fileChooser.getSelectedFile().getAbsolutePath());
                    buildBoardUI();
                } catch (Exception ex) {}
            }
        });
    }

    private void openEffectConfigDialog(String title, boolean isBlue) {
        String current = isBlue ? board.getBlueEffect() : board.getRedEffect();
        current = current.replace("|", "\n");
        
        JTextArea ta = new JTextArea(current, 8, 20);
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel("フォーマット: [金額],[確率(%)] を1行ずつ入力 (合計100%)"), BorderLayout.NORTH);
        p.add(new JScrollPane(ta), BorderLayout.CENTER);
        
        while (true) {
            int res = JOptionPane.showConfirmDialog(this, p, title, JOptionPane.OK_CANCEL_OPTION);
            if (res == JOptionPane.OK_OPTION) {
                String confRaw = ta.getText().trim();
                String[] lines = confRaw.split("\n");
                int totalProb = 0;
                boolean isFormatError = false;
                
                for (String line : lines) {
                    if(line.trim().isEmpty()) continue;
                    String[] kv = line.split(",");
                    if (kv.length >= 2) {
                        try {
                            totalProb += Integer.parseInt(kv[1].trim());
                        } catch (NumberFormatException ex) {
                            isFormatError = true;
                        }
                    } else {
                        isFormatError = true;
                    }
                }
                
                if (isFormatError) {
                    JOptionPane.showMessageDialog(this, "入力フォーマットに誤りがあります。\n例: 1000,20", "フォーマットエラー", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                
                if (totalProb != 100) {
                    JOptionPane.showMessageDialog(this, "確率の合計が 100% になりません！\n現在の合計: " + totalProb + "%", "確率エラー", JOptionPane.ERROR_MESSAGE);
                    continue;
                }
                
                String conf = confRaw.replace("\n", "|");
                if (isBlue) board.setBlueEffect(conf);
                else board.setRedEffect(conf);
                break;
            } else {
                break;
            }
        }
    }

    private void buildBoardUI() {
        gridPanel.removeAll();
        int w = board.getWidth();
        int h = board.getHeight();
        gridPanel.setLayout(new GridLayout(h, w, 2, 2));
        massButtons = new JButton[h][w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                final int fx = x, fy = y;
                JButton btn = new JButton();
                updateButtonAppearance(btn, fx, fy);
                
                btn.addActionListener(e -> {
                    int currentType = board.getTile(fx, fy);
                    String[] options = {"0: 白(通常)", "1: 青(プラス)", "2: 赤(マイナス)", "3: 黄(カード)", "4: 紫(貧乏神)", "5: 緑(物件)", "6: 黒(空白)"};
                    String selected = (String) JOptionPane.showInputDialog(this, "マスの種類を選択してください", "マス設定", JOptionPane.PLAIN_MESSAGE, null, options, options[currentType]);
                    
                    if (selected != null) {
                        int nextType = Arrays.asList(options).indexOf(selected);
                        board.setTile(fx, fy, nextType);
                        if (nextType == 5) showPropertyConfigDialog(fx, fy);
                        updateButtonAppearance(btn, fx, fy);
                    }
                });
                
                massButtons[y][x] = btn;
                gridPanel.add(btn);
            }
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void showPropertyConfigDialog(int x, int y) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        String fullData = board.getStationData(x, y);
        String sName = fullData.split("\\|")[0];
        StringBuilder propsText = new StringBuilder();

        if (fullData.contains("|")) {
            String[] parts = fullData.split("\\|");
            for (int i = 1; i < parts.length; i++) {
                propsText.append(parts[i]).append("\n");
            }
        } else {
            propsText.append("屋台,1000,0.5\nタワー,5000,0.2\n");
        }

        JTextField nameField = new JTextField(sName);
        JTextArea propsArea = new JTextArea(6, 25);
        propsArea.setText(propsText.toString());

        panel.add(new JLabel("駅名:"));
        panel.add(nameField);
        panel.add(Box.createVerticalStrut(10));
        panel.add(new JLabel("物件 (物件名,金額,収益率(小数) を1行ずつ入力):"));
        panel.add(new JScrollPane(propsArea));

        int res = JOptionPane.showConfirmDialog(this, panel, "物件マスの設定", JOptionPane.OK_CANCEL_OPTION);
        if (res == JOptionPane.OK_OPTION) {
            StringBuilder newData = new StringBuilder(nameField.getText().trim());
            for (String line : propsArea.getText().split("\n")) {
                if (!line.trim().isEmpty()) {
                    newData.append("|").append(line.trim());
                }
            }
            board.setStationData(x, y, newData.toString());
        }
    }

    private void updateButtonAppearance(JButton btn, int x, int y) {
        int type = board.getTile(x, y);
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        
        String text = "";
        if (type == 0) { btn.setBackground(Color.WHITE); text = "白"; }
        else if (type == 1) { btn.setBackground(new Color(150, 200, 255)); text = "青"; }
        else if (type == 2) { btn.setBackground(new Color(255, 150, 150)); text = "赤"; }
        else if (type == 3) { btn.setBackground(new Color(255, 230, 100)); text = "黄"; }
        else if (type == 4) { btn.setBackground(new Color(180, 130, 220)); text = "紫"; }
        else if (type == 5) { btn.setBackground(new Color(140, 230, 140)); text = board.getStationName(x, y); }
        else if (type == 6) { btn.setBackground(Color.DARK_GRAY); btn.setForeground(Color.WHITE); text = "空白"; } 

        if (x == board.getStartX() && y == board.getStartY()) {
            text = "🚩" + text;
            btn.setBorder(BorderFactory.createLineBorder(Color.RED, 3));
        } else {
            btn.setBorder(UIManager.getBorder("Button.border"));
            btn.setForeground(Color.BLACK);
            if (type == 6) btn.setForeground(Color.WHITE);
        }
        btn.setText(text);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BoardEditor().setVisible(true));
    }
}