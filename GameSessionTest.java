// Students: CSY23102, CSY23052, CSY23031

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

/**
 * JUnit 4 tests for GameSession: makeMove, win/draw, getResultFor, invalid moves.
 * Tests the core game logic including move validation, turn alternation, win detection,
 * and game over conditions. These tests ensure the game rules are correctly enforced
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
        // test that the first player (alice) can make a valid move
        Assert.assertTrue(session.makeMove(ALICE, 1, 1));
        // turn should switch to bob after alice's move
        Assert.assertEquals(BOB, session.getCurrentPlayer());
        // game should not be over yet
        Assert.assertFalse(session.isGameOver());
    }

    @Test
    public void makeMove_wrongPlayerRejected() {
        // test that bob cannot move when it's alice's turn
        Assert.assertFalse(session.makeMove(BOB, 0, 0));
        // alice should still be the current player
        Assert.assertEquals(ALICE, session.getCurrentPlayer());
    }

    @Test
    public void makeMove_outOfBoundsRejected() {
        // test that moves outside the 0-2 grid are rejected
        Assert.assertFalse(session.makeMove(ALICE, -1, 0));
        Assert.assertFalse(session.makeMove(ALICE, 0, -1));
        Assert.assertFalse(session.makeMove(ALICE, 3, 0));
        Assert.assertFalse(session.makeMove(ALICE, 0, 3));
    }

    @Test
    public void makeMove_occupiedCellRejected() {
        // test that two players can't place on the same cell
        session.makeMove(ALICE, 0, 0);
        Assert.assertFalse(session.makeMove(BOB, 0, 0));
    }

    @Test
    public void makeMove_afterGameOverRejected() {
        // test that no more moves are allowed after game ends
        // X wins: (0,0),(1,0),(2,0) for Alice; (0,1),(1,1) for Bob
        session.makeMove(ALICE, 0, 0);
        session.makeMove(BOB, 0, 1);
        session.makeMove(ALICE, 1, 0);
        session.makeMove(BOB, 1, 1);
        session.makeMove(ALICE, 2, 0);
        Assert.assertTrue(session.isGameOver());
        // bob tries to move after game is over
        Assert.assertFalse(session.makeMove(BOB, 2, 2));
    }

    @Test
    public void win_row() {
        // test that three in a row (any row) is detected as a win
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
        // test that three in a column is detected as a win
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
        // test that diagonal from top-left to bottom-right is detected as a win
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
        // test that diagonal from top-right to bottom-left is detected as a win
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
        // test that when all 9 cells are filled with no winner, it's a draw
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
        // test that an ongoing game returns ONGOING status
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
