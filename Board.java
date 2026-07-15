// v2.6

import java.io.*;
import java.util.*;
import java.awt.Point;

public class Board {
    private int width;
    private int height;
    private int[][] grid; 
    private Map<String, String> stationData = new HashMap<>();
    private int goalX;
    private int goalY;
    
    private int startX = 0;
    private int startY = 0;

    private Random rand = new Random();
    private String blueEffect = "1000,20|2000,20|3000,20|4000,20|5000,20";
    private String redEffect = "1000,20|2000,20|3000,20|4000,20|5000,20";

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
        boolean hasProperty = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = rand.nextInt(20);
                if (r < 6) grid[y][x] = 0;
                else if (r < 10) grid[y][x] = 1;
                else if (r < 12) grid[y][x] = 2;
                else if (r < 13) grid[y][x] = 3;
                else if (r < 14) grid[y][x] = 4;
                else if (r < 16) grid[y][x] = 6; 
                else {
                    grid[y][x] = 5;
                    stationData.put(x + "," + y, DEFAULT_STATIONS[stationIdx % DEFAULT_STATIONS.length]);
                    stationIdx++;
                    hasProperty = true;
                }
            }
        }
        if (!hasProperty) {
            grid[0][0] = 5;
            stationData.put("0,0", "新宿");
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
        
        if (this.startX >= newWidth) this.startX = newWidth - 1;
        if (this.startY >= newHeight) this.startY = newHeight - 1;

        relocateGoal();
    }

    public void relocateGoal() {
        List<Point> propertyTiles = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (grid[y][x] == 5) propertyTiles.add(new Point(x, y));
            }
        }
        if (!propertyTiles.isEmpty()) {
            Point p = propertyTiles.get(rand.nextInt(propertyTiles.size()));
            goalX = p.x;
            goalY = p.y;
        } else {
            goalX = rand.nextInt(width);
            goalY = rand.nextInt(height);
        }
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getTile(int x, int y) { return grid[y][x]; }
    public void setTile(int x, int y, int type) { grid[y][x] = type; }
    public int getGoalX() { return goalX; }
    public int getGoalY() { return goalY; }
    
    public int getStartX() { return startX; }
    public int getStartY() { return startY; }
    public void setStartPos(int x, int y) { this.startX = x; this.startY = y; }
    
    public String getStationData(int x, int y) { return stationData.getOrDefault(x + "," + y, "名無し駅"); }
    public void setStationData(int x, int y, String data) { stationData.put(x + "," + y, data); }
    public String getStationName(int x, int y) { return getStationData(x, y).split("\\|")[0]; }

    public String getBlueEffect() { return blueEffect; }
    public void setBlueEffect(String conf) { this.blueEffect = conf; }
    public String getRedEffect() { return redEffect; }
    public void setRedEffect(String conf) { this.redEffect = conf; }

    public void saveToFile(String filename) throws IOException {
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(filename)))) {
            pw.println("START_POS:" + startX + "," + startY); 
            pw.println("BLUE_CONF:" + blueEffect);
            pw.println("RED_CONF:" + redEffect);
            
            pw.println(width + "," + height);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    pw.print(grid[y][x]);
                    if (x < width - 1) pw.print(",");
                }
                pw.println();
            }
            for (Map.Entry<String, String> entry : stationData.entrySet()) {
                pw.println("STATION:" + entry.getKey() + ":" + entry.getValue());
            }
        }
    }

    public void loadFromFile(String filename) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("START_POS:")) {
                    String[] parts = line.substring(10).split(",");
                    this.startX = Integer.parseInt(parts[0]);
                    this.startY = Integer.parseInt(parts[1]);
                    continue;
                }
                if (line.startsWith("BLUE_CONF:")) { blueEffect = line.substring(10); continue; }
                if (line.startsWith("RED_CONF:")) { redEffect = line.substring(9); continue; }
                if (line.contains(",")) {
                    String[] dims = line.split(",");
                    if (dims.length == 2 && !line.startsWith("STATION")) {
                        this.width = Integer.parseInt(dims[0]);
                        this.height = Integer.parseInt(dims[1]);
                        break;
                    }
                }
            }

            this.grid = new int[height][width];
            this.stationData.clear();

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
                    String[] parts = line.split(":", 3);
                    if (parts.length >= 3) stationData.put(parts[1], parts[2]);
                }
            }
            relocateGoal();
        }
    }
}