package jp.sugoroku;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

class SugorokuGameTest {
    @Test
    void turnProgressesAndWinnerIsSet() {
        SugorokuGame game = new SugorokuGame(List.of("A", "B"), 10, new FixedRandom(2, 3, 5));

        SugorokuGame.TurnResult t1 = game.playTurn();
        assertEquals("A", t1.player());
        assertEquals(3, t1.roll());
        assertEquals(3, t1.position());
        assertFalse(t1.finished());
        assertEquals("B", game.getCurrentPlayer());

        SugorokuGame.TurnResult t2 = game.playTurn();
        assertEquals("B", t2.player());
        assertEquals(4, t2.roll());
        assertEquals(4, t2.position());
        assertFalse(t2.finished());

        SugorokuGame.TurnResult t3 = game.playTurn();
        assertEquals("A", t3.player());
        assertEquals(6, t3.roll());
        assertEquals(9, t3.position());
        assertFalse(t3.finished());
    }

    @Test
    void reachingGoalEndsGame() {
        SugorokuGame game = new SugorokuGame(List.of("A", "B"), 5, new FixedRandom(5));

        SugorokuGame.TurnResult t1 = game.playTurn();

        assertTrue(t1.finished());
        assertEquals("A", game.getWinner());
        assertTrue(game.isFinished());
    }

    private static class FixedRandom extends Random {
        private final int[] values;
        private int index;

        private FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            int value = values[index % values.length];
            index++;
            return value;
        }
    }
}
