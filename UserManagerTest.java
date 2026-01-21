// Students: CSY23102, CSY23052, CSY23031

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * JUnit 4 tests for UserManager: register, login, updateStats, getLeaderboard.
 * Tests user persistence, authentication, and statistics tracking for the leaderboard.
 * Uses temporary test files to ensure tests don't pollute real data
 */
public class UserManagerTest {

    private File tempUsers;
    private UserManager um;

    @Before
    public void setUp() throws IOException {
        // create a temporary test file for each test so they don't interfere with each other
        tempUsers = File.createTempFile("umtest", ".txt");
        tempUsers.deleteOnExit();
        um = new UserManager(tempUsers.getAbsolutePath());
    }

    @After
    public void tearDown() {
        if (tempUsers != null && tempUsers.exists()) {
            tempUsers.delete();
        }
    }

    @Test
    public void register_success() {
        // test that a new user can be registered successfully
        Assert.assertTrue(um.register("alice", "secret123"));
        Assert.assertNotNull(um.getUser("alice"));
    }

    @Test
    public void register_duplicate_returnsFalse() {
        // test that registering the same username twice fails
        Assert.assertTrue(um.register("bob", "pass"));
        Assert.assertFalse(um.register("bob", "other"));
    }

    @Test
    public void login_success_afterRegister() {
        // test that after registering, a user can login with correct password
        um.register("alice", "secret123");
        Assert.assertTrue(um.login("alice", "secret123"));
    }

    @Test
    public void login_wrongPassword_fails() {
        // test that login fails if password is incorrect
        um.register("alice", "secret123");
        Assert.assertFalse(um.login("alice", "wrong"));
    }

    @Test
    public void login_nonexistentUser_fails() {
        // test that login for a user that was never registered fails
        Assert.assertFalse(um.login("nobody", "x"));
    }

    @Test
    public void updateStats_winIncrementsWins() {
        // test that recording a WIN increments the wins counter
        um.register("alice", "p");
        um.updateStats("alice", "WIN");
        Assert.assertEquals(1, um.getUser("alice").getWins());
    }

    @Test
    public void updateStats_lossIncrementsLosses() {
        // test that recording a LOSS increments the losses counter
        um.register("bob", "p");
        um.updateStats("bob", "LOSS");
        Assert.assertEquals(1, um.getUser("bob").getLosses());
    }

    @Test
    public void updateStats_drawIncrementsDraws() {
        // test that recording a DRAW increments the draws counter
        um.register("carol", "p");
        um.updateStats("carol", "DRAW");
        Assert.assertEquals(1, um.getUser("carol").getDraws());
    }

    @Test
    public void updateStats_unknownUser_ignored() {
        // test that updating stats for a nonexistent user doesn't affect other users
        um.register("alice", "p");
        um.updateStats("nobody", "WIN");
        Assert.assertEquals(0, um.getUser("alice").getWins());
    }

    @Test
    public void getLeaderboard_ordersByWinsThenTotalGames() {
        // test that leaderboard sorts correctly: by wins first, then by total games as tiebreaker
        // a: 2 wins (2 games)
        // b: 1 win, 1 loss (2 games)
        // c: 1 draw (1 game)
        um.register("a", "p");
        um.register("b", "p");
        um.register("c", "p");
        um.updateStats("a", "WIN");
        um.updateStats("a", "WIN");
        um.updateStats("b", "WIN");
        um.updateStats("b", "LOSS");
        um.updateStats("c", "DRAW");

        List<UserManager.User> top = um.getLeaderboard(10);
        Assert.assertEquals(3, top.size());
        // a should be first (2 wins)
        Assert.assertEquals("a", top.get(0).getUsername());
        Assert.assertEquals(2, top.get(0).getWins());
        // b should be second (1 win, 2 games played)
        Assert.assertEquals("b", top.get(1).getUsername());
        Assert.assertEquals(1, top.get(1).getWins());
        // c should be last (0 wins)
        Assert.assertEquals("c", top.get(2).getUsername());
    }

    @Test
    public void getLeaderboard_respectsLimit() {
        // test that the limit parameter correctly limits results (for leaderboard top N)
        um.register("a", "p");
        um.register("b", "p");
        um.register("c", "p");
        List<UserManager.User> top = um.getLeaderboard(2);
        Assert.assertEquals(2, top.size());
    }

    @Test
    public void setOnline_setOffline_isOnline() {
        um.register("alice", "p");
        Assert.assertFalse(um.isOnline("alice"));
        um.setOnline("alice", "s1");
        Assert.assertTrue(um.isOnline("alice"));
        um.setOffline("alice");
        Assert.assertFalse(um.isOnline("alice"));
    }
}
