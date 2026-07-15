//v2.4

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class BoardEditor extends JFrame {
    private Board board;
    private JPanel gridPanel;
    private JButton[][] massButtons;
    private JLabel statusLabel;

    public BoardEditor() {
        setTitle("2Dマップエディタ (物件対応版)");
        setSize(850, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        board = new Board(10, 10);

        JPanel menuPanel = new JPanel();
        JButton saveButton = new JButton("💾 CSV保存");
        JButton loadButton = new JButton("📂 CSV読込");
        JButton resizeButton = new JButton("⚙ サイズ変更");
        statusLabel = new JLabel("クリックで変更 (白:通常 青:プラス 赤:マイナス 黄:カード 紫:貧乏神 緑:物件)");
        
        menuPanel.add(saveButton);
        menuPanel.add(loadButton);
        menuPanel.add(resizeButton);
        menuPanel.add(statusLabel);
        add(menuPanel, BorderLayout.NORTH);

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
                    int nextType = (currentType + 1) % 6;
                    board.setTile(fx, fy, nextType);
                    
                    if (nextType == 5) {
                        String stationName = JOptionPane.showInputDialog(this, "物件マスの駅名を入力してください:", board.getStationName(fx, fy));
                        if (stationName != null && !stationName.isEmpty()) {
                            board.setStationName(fx, fy, stationName);
                        }
                    }
                    updateButtonAppearance(btn, fx, fy);
                });
                
                massButtons[y][x] = btn;
                gridPanel.add(btn);
            }
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void updateButtonAppearance(JButton btn, int x, int y) {
        int type = board.getTile(x, y);
        // 【変更点】文字化け対策のため Arial から SansSerif に変更
        btn.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        if (type == 0) { btn.setBackground(Color.WHITE); btn.setText("白"); }
        else if (type == 1) { btn.setBackground(new Color(150, 200, 255)); btn.setText("青"); }
        else if (type == 2) { btn.setBackground(new Color(255, 150, 150)); btn.setText("赤"); }
        else if (type == 3) { btn.setBackground(new Color(255, 230, 100)); btn.setText("黄"); }
        else if (type == 4) { btn.setBackground(new Color(180, 130, 220)); btn.setText("紫"); }
        else if (type == 5) { btn.setBackground(new Color(140, 230, 140)); btn.setText(board.getStationName(x, y)); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BoardEditor().setVisible(true));
    }
}