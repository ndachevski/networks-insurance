import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * JUnit 4 tests for UserManager: register, login, updateStats, getLeaderboard.
 */
public class UserManagerTest {

    private File tempUsers;
    private UserManager um;

    @Before
    public void setUp() throws IOException {
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
        Assert.assertTrue(um.register("alice", "secret123"));
        Assert.assertNotNull(um.getUser("alice"));
    }

    @Test
    public void register_duplicate_returnsFalse() {
        Assert.assertTrue(um.register("bob", "pass"));
        Assert.assertFalse(um.register("bob", "other"));
    }

    @Test
    public void login_success_afterRegister() {
        um.register("alice", "secret123");
        Assert.assertTrue(um.login("alice", "secret123"));
    }

    @Test
    public void login_wrongPassword_fails() {
        um.register("alice", "secret123");
        Assert.assertFalse(um.login("alice", "wrong"));
    }

    @Test
    public void login_nonexistentUser_fails() {
        Assert.assertFalse(um.login("nobody", "x"));
    }

    @Test
    public void updateStats_winIncrementsWins() {
        um.register("alice", "p");
        um.updateStats("alice", "WIN");
        Assert.assertEquals(1, um.getUser("alice").getWins());
    }

    @Test
    public void updateStats_lossIncrementsLosses() {
        um.register("bob", "p");
        um.updateStats("bob", "LOSS");
        Assert.assertEquals(1, um.getUser("bob").getLosses());
    }

    @Test
    public void updateStats_drawIncrementsDraws() {
        um.register("carol", "p");
        um.updateStats("carol", "DRAW");
        Assert.assertEquals(1, um.getUser("carol").getDraws());
    }

    @Test
    public void updateStats_unknownUser_ignored() {
        um.register("alice", "p");
        um.updateStats("nobody", "WIN");
        Assert.assertEquals(0, um.getUser("alice").getWins());
    }

    @Test
    public void getLeaderboard_ordersByWinsThenTotalGames() {
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
        Assert.assertEquals("a", top.get(0).getUsername());
        Assert.assertEquals(2, top.get(0).getWins());
        Assert.assertEquals("b", top.get(1).getUsername());
        Assert.assertEquals(1, top.get(1).getWins());
        Assert.assertEquals("c", top.get(2).getUsername());
    }

    @Test
    public void getLeaderboard_respectsLimit() {
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
