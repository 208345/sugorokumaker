package jp.sugoroku;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class SugorokuGame {
    private final Map<String, Integer> positions;
    private final List<String> turnOrder;
    private final int goal;
    private final Random random;
    private int turnIndex;
    private String winner;

    public SugorokuGame(List<String> players, int goal, Random random) {
        if (players == null || players.size() < 2) {
            throw new IllegalArgumentException("2人以上のプレイヤーが必要です");
        }
        if (goal < 1) {
            throw new IllegalArgumentException("ゴールマスは1以上である必要があります");
        }
        this.turnOrder = List.copyOf(players);
        this.goal = goal;
        this.random = Objects.requireNonNull(random, "random must not be null");
        this.positions = new LinkedHashMap<>();
        for (String player : turnOrder) {
            positions.put(player, 0);
        }
    }

    public static SugorokuGame forTwoPlayers(String player1, String player2, int goal) {
        return new SugorokuGame(List.of(player1, player2), goal, new Random());
    }

    public String getCurrentPlayer() {
        return turnOrder.get(turnIndex);
    }

    public Map<String, Integer> getPositions() {
        return Collections.unmodifiableMap(positions);
    }

    public boolean isFinished() {
        return winner != null;
    }

    public String getWinner() {
        return winner;
    }

    public TurnResult playTurn() {
        if (isFinished()) {
            throw new IllegalStateException("ゲームは終了しています");
        }
        String player = getCurrentPlayer();
        int roll = random.nextInt(6) + 1;
        int newPosition = Math.min(goal, positions.get(player) + roll);
        positions.put(player, newPosition);
        if (newPosition >= goal) {
            winner = player;
        } else {
            turnIndex = (turnIndex + 1) % turnOrder.size();
        }
        return new TurnResult(player, roll, newPosition, winner != null);
    }

    public record TurnResult(String player, int roll, int position, boolean finished) {
    }
}
