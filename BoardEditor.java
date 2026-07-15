//v2.3

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;

public class BoardEditor extends JFrame {
    private Board board;
    private JPanel gridPanel;
    private JButton[][] massButtons;
    private JLabel statusLabel;

    public BoardEditor() {
        setTitle("2Dマップエディタ");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        board = new Board(10, 10); // 初期サイズは10x10

        JPanel menuPanel = new JPanel();
        JButton saveButton = new JButton("💾 CSV保存");
        JButton loadButton = new JButton("📂 CSV読込");
        JButton resizeButton = new JButton("⚙ サイズ変更");
        statusLabel = new JLabel("マスをクリックで変更 (白:通常 青:プラス 赤:マイナス 黄:アイテム 紫:貧乏神)");
        
        menuPanel.add(saveButton);
        menuPanel.add(loadButton);
        menuPanel.add(resizeButton);
        menuPanel.add(statusLabel);
        add(menuPanel, BorderLayout.NORTH);

        gridPanel = new JPanel();
        add(gridPanel, BorderLayout.CENTER);

        buildBoardUI();

        resizeButton.addActionListener(e -> {
            String wStr = JOptionPane.showInputDialog(this, "新しい【横】のマス数を入力 (例: 10)", board.getWidth());
            String hStr = JOptionPane.showInputDialog(this, "新しい【縦】のマス数を入力 (例: 10)", board.getHeight());
            if (wStr != null && hStr != null) {
                try {
                    board.resize(Integer.parseInt(wStr.trim()), Integer.parseInt(hStr.trim()));
                    buildBoardUI();
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(this, "数値を入力してください。");
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
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "保存失敗: " + ex.getMessage());
                }
            }
        });

        loadButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                try {
                    board.loadFromFile(fileChooser.getSelectedFile().getAbsolutePath());
                    buildBoardUI();
                    JOptionPane.showMessageDialog(this, "読み込みました。");
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "読込失敗。ファイル形式を確認してください。");
                }
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
                updateButtonAppearance(btn, board.getTile(fx, fy));
                
                // クリックでマスの種類を 0→1→2→3→4→0 とローテーションさせる
                btn.addActionListener(e -> {
                    int currentType = board.getTile(fx, fy);
                    int nextType = (currentType + 1) % 5;
                    board.setTile(fx, fy, nextType);
                    updateButtonAppearance(btn, nextType);
                });
                
                massButtons[y][x] = btn;
                gridPanel.add(btn);
            }
        }
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private void updateButtonAppearance(JButton btn, int type) {
        btn.setFont(new Font("Arial", Font.BOLD, 12));
        if (type == 0) { btn.setBackground(Color.WHITE); btn.setText("白"); }
        else if (type == 1) { btn.setBackground(new Color(150, 200, 255)); btn.setText("青"); }
        else if (type == 2) { btn.setBackground(new Color(255, 150, 150)); btn.setText("赤"); }
        else if (type == 3) { btn.setBackground(new Color(255, 230, 100)); btn.setText("黄"); }
        else if (type == 4) { btn.setBackground(new Color(180, 130, 220)); btn.setText("紫"); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new BoardEditor().setVisible(true));
    }
}