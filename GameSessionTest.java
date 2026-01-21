import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

/**
 * JUnit 4 tests for GameSession: makeMove, win/draw, getResultFor, invalid moves.
 */
public class GameSessionTest {

    private GameSession session;
    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    @Before
    public void setUp() {
        session = new GameSession("g1", ALICE, BOB);
    }

    @Test
    public void makeMove_validFirstMove() {
        Assert.assertTrue(session.makeMove(ALICE, 1, 1));
        Assert.assertEquals(BOB, session.getCurrentPlayer());
        Assert.assertFalse(session.isGameOver());
    }

    @Test
    public void makeMove_wrongPlayerRejected() {
        Assert.assertFalse(session.makeMove(BOB, 0, 0));
        Assert.assertEquals(ALICE, session.getCurrentPlayer());
    }

    @Test
    public void makeMove_outOfBoundsRejected() {
        Assert.assertFalse(session.makeMove(ALICE, -1, 0));
        Assert.assertFalse(session.makeMove(ALICE, 0, -1));
        Assert.assertFalse(session.makeMove(ALICE, 3, 0));
        Assert.assertFalse(session.makeMove(ALICE, 0, 3));
    }

    @Test
    public void makeMove_occupiedCellRejected() {
        session.makeMove(ALICE, 0, 0);
        Assert.assertFalse(session.makeMove(BOB, 0, 0));
    }

    @Test
    public void makeMove_afterGameOverRejected() {
        // X wins: (0,0),(1,0),(2,0) for Alice; (0,1),(1,1) for Bob
        session.makeMove(ALICE, 0, 0);
        session.makeMove(BOB, 0, 1);
        session.makeMove(ALICE, 1, 0);
        session.makeMove(BOB, 1, 1);
        session.makeMove(ALICE, 2, 0);
        Assert.assertTrue(session.isGameOver());
        Assert.assertFalse(session.makeMove(BOB, 2, 2));
    }

    @Test
    public void win_row() {
        session.makeMove(ALICE, 0, 0);
        session.makeMove(BOB, 1, 0);
        session.makeMove(ALICE, 0, 1);
        session.makeMove(BOB, 1, 1);
        session.makeMove(ALICE, 0, 2);
        Assert.assertTrue(session.isGameOver());
        Assert.assertEquals(ALICE, session.getWinner());
        Assert.assertEquals("WIN", session.getResultFor(ALICE));
        Assert.assertEquals("LOSS", session.getResultFor(BOB));
    }

    @Test
    public void win_column() {
        session.makeMove(ALICE, 1, 0);
        session.makeMove(BOB, 0, 1);
        session.makeMove(ALICE, 1, 1);
        session.makeMove(BOB, 0, 2);
        session.makeMove(ALICE, 1, 2);
        Assert.assertTrue(session.isGameOver());
        Assert.assertEquals(ALICE, session.getWinner());
    }

    @Test
    public void win_diagonalTopLeftToBottomRight() {
        session.makeMove(ALICE, 0, 0);
        session.makeMove(BOB, 0, 1);
        session.makeMove(ALICE, 1, 1);
        session.makeMove(BOB, 0, 2);
        session.makeMove(ALICE, 2, 2);
        Assert.assertTrue(session.isGameOver());
        Assert.assertEquals(ALICE, session.getWinner());
    }

    @Test
    public void win_diagonalTopRightToBottomLeft() {
        session.makeMove(ALICE, 0, 2);
        session.makeMove(BOB, 0, 0);
        session.makeMove(ALICE, 1, 1);
        session.makeMove(BOB, 1, 0);
        session.makeMove(ALICE, 2, 0);
        Assert.assertTrue(session.isGameOver());
        Assert.assertEquals(ALICE, session.getWinner());
    }

    @Test
    public void draw() {
        session.makeMove(ALICE, 0, 0);
        session.makeMove(BOB, 0, 1);
        session.makeMove(ALICE, 0, 2);
        session.makeMove(BOB, 1, 0);
        session.makeMove(ALICE, 1, 1);
        session.makeMove(BOB, 2, 0);
        session.makeMove(ALICE, 1, 2);
        session.makeMove(BOB, 2, 2);
        session.makeMove(ALICE, 2, 1);
        Assert.assertTrue(session.isGameOver());
        Assert.assertEquals("DRAW", session.getWinner());
        Assert.assertEquals("DRAW", session.getResultFor(ALICE));
        Assert.assertEquals("DRAW", session.getResultFor(BOB));
    }

    @Test
    public void getResultFor_ongoing() {
        session.makeMove(ALICE, 0, 0);
        Assert.assertEquals("ONGOING", session.getResultFor(ALICE));
        Assert.assertEquals("ONGOING", session.getResultFor(BOB));
    }

    @Test
    public void getBoardMap() {
        session.makeMove(ALICE, 1, 1);
        session.makeMove(BOB, 0, 0);
        Map<String, String> b = session.getBoardMap();
        Assert.assertEquals("X", b.get("1,1"));
        Assert.assertEquals("O", b.get("0,0"));
        Assert.assertEquals(" ", b.get("2,2"));
    }
}
