//v2.4

import java.io.*;
import java.util.*;

public class Board {
    private int width;
    private int height;
    private int[][] grid; // 0:白, 1:青, 2:赤, 3:黄, 4:紫(貧乏神), 5:緑(物件)
    private Map<String, String> stationNames = new HashMap<>(); // "x,y" -> "駅名"
    private int goalX;
    private int goalY;
    private Random rand = new Random();

    // デフォルトの地名リスト
    private static final String[] DEFAULT_STATIONS = {"新宿", "池袋", "八王子", "早稲田", "渋谷", "品川", "東京", "秋葉原", "上野", "横浜", "立川", "吉祥寺"};

    public Board(int width, int height) {
        this.width = width;
        this.height = height;
        this.grid = new int[height][width];
        initGrid();
        relocateGoal();
    }

    public Board(String filename) throws IOException {
        loadFromFile(filename);
    }

    private void initGrid() {
        int stationIdx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rand.nextInt(20);
                if (r < 6) grid[y][x] = 0;
                else if (r < 10) grid[y][x] = 1;
                else if (r < 12) grid[y][x] = 2;
                else if (r < 13) grid[y][x] = 3;
                else if (r < 14) grid[y][x] = 4;
                else {
                    grid[y][x] = 5; // 物件マス
                    stationNames.put(x + "," + y, DEFAULT_STATIONS[stationIdx % DEFAULT_STATIONS.length]);
                    stationIdx++;
                }
            }
        }
    }

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
    
    public String getStationName(int x, int y) {
        return stationNames.getOrDefault(x + "," + y, "名無し駅");
    }
    public void setStationName(int x, int y, String name) {
        stationNames.put(x + "," + y, name);
    }

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
            // 駅名データの保存
            for (Map.Entry<String, String> entry : stationNames.entrySet()) {
                pw.println("STATION:" + entry.getKey() + ":" + entry.getValue());
            }
        }
    }

    public void loadFromFile(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line = br.readLine();
            if (line == null) throw new IOException("ファイルが空です。");
            String[] dims = line.split(",");
            this.width = Integer.parseInt(dims[0]);
            this.height = Integer.parseInt(dims[1]);
            this.grid = new int[height][width];
            this.stationNames.clear();

            for (int y = 0; y < height; y++) {
                line = br.readLine();
                if (line == null) break;
                String[] tiles = line.split(",");
                for (int x = 0; x < width; x++) {
                    this.grid[y][x] = Integer.parseInt(tiles[x]);
                }
            }
            
            while ((line = br.readLine()) != null) {
                if (line.startsWith("STATION:")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 3) {
                        stationNames.put(parts[1], parts[2]);
                    }
                }
            }
            relocateGoal();
        }
    }
}