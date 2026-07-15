//v2.3

import java.io.*;
import java.util.Random;

public class Board {
    private int width;
    private int height;
    private int[][] grid; // 0:白(通常), 1:青(プラス), 2:赤(マイナス), 3:黄(アイテム), 4:紫(貧乏神)
    private int goalX;
    private int goalY;
    private Random rand = new Random();

    // 新規作成用のコンストラクタ
    public Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new int[height][width];
        initGrid();
        relocateGoal();
    }

    // CSVから読み込むコンストラクタ
    public Board(String filename) throws IOException {
        loadFromFile(filename);
    }

    private void initGrid() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rand.nextInt(15);
                if (r < 6) grid[y][x] = 0;
                else if (r < 10) grid[y][x] = 1;
                else if (r < 12) grid[y][x] = 2;
                else if (r < 13) grid[y][x] = 3;
                else grid[y][x] = 4;
            }
        }
    }

    // マス数を変更（エディタ用）
    public void resize(int newWidth, int newHeight) {
        int[][] newGrid = new int[newHeight][newWidth];
        for (int y = 0; y < Math.min(this.height, newHeight); y++) {
            for (int x = 0; x < Math.min(this.width, newWidth); x++) {
                newGrid[y][x] = this.grid[y][x];
            }
        }
        this.width = newWidth;
        this.height = newHeight;
        this.grid = newGrid;
        relocateGoal();
    }

    public void relocateGoal() {
        goalX = rand.nextInt(width);
        goalY = rand.nextInt(height);
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTile(int x, int y) { return grid[y][x]; }
    public void setTile(int x, int y, int type) { grid[y][x] = type; }
    public int getGoalX() { return goalX; }
    public int getGoalY() { return goalY; }

    // CSVへ保存 (1行目に幅と高さ、2行目以降にカンマ区切りで配置データを保存)
    public void saveToFile(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            pw.println(width + "," + height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pw.print(grid[y][x]);
                    if (x < width - 1) pw.print(",");
                }
                pw.println();
            }
        }
    }

    // CSVから読込
    public void loadFromFile(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();
            if (line == null) throw new IOException("ファイルが空です。");
            String[] dims = line.split(",");
            this.width = Integer.parseInt(dims[0]);
            this.height = Integer.parseInt(dims[1]);
            this.grid = new int[height][width];

            for (int y = 0; y < height; y++) {
                line = br.readLine();
                if (line == null) break;
                String[] tiles = line.split(",");
                for (int x = 0; x < width; x++) {
                    this.grid[y][x] = Integer.parseInt(tiles[x]);
                }
            }
            relocateGoal();
        }
    }
}