import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * JUnit 4 tests for GameServer: processMove validation, handleLeaveGame, disconnect/forfeit.
 * Uses a TestClientHandler to capture messages without real network I/O.
 */
public class GameServerTest {

    private UserManager userManager;
    private GameServer server;
    private File tempUsers;
    private TestClientHandler aliceHandler;
    private TestClientHandler bobHandler;
    private Socket aliceSocket;
    private Socket bobSocket;

    @Before
    public void setUp() throws IOException {
        tempUsers = File.createTempFile("users", ".txt");
        tempUsers.deleteOnExit();
        userManager = new UserManager(tempUsers.getAbsolutePath());
        Assert.assertTrue(userManager.register("alice", "pass1"));
        Assert.assertTrue(userManager.register("bob", "pass2"));

        server = new GameServer(userManager);

        aliceSocket = createConnectedSocket();
        bobSocket = createConnectedSocket();
        aliceHandler = new TestClientHandler(aliceSocket, userManager, server, "alice");
        bobHandler = new TestClientHandler(bobSocket, userManager, server, "bob");

        server.addClient(aliceHandler);
        server.addClient(bobHandler);
    }

    private Socket createConnectedSocket() throws IOException {
        ServerSocket ss = new ServerSocket(0);
        try (Socket client = new Socket("127.0.0.1", ss.getLocalPort())) {
            Socket serverSide = ss.accept();
            ss.close();
            return serverSide;
        }
    }

    @After
    public void tearDown() throws IOException {
        if (aliceSocket != null && !aliceSocket.isClosed()) aliceSocket.close();
        if (bobSocket != null && !bobSocket.isClosed()) bobSocket.close();
        if (tempUsers != null && tempUsers.exists()) tempUsers.delete();
    }

    private void injectGame(String gameId, String p1, String p2) throws Exception {
        Field f = GameServer.class.getDeclaredField("games");
        f.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, GameSession> games = (Map<String, GameSession>) f.get(server);
        games.put(gameId, new GameSession(gameId, p1, p2));
    }

    @Test
    public void processMove_validMove_sendsUpdate() throws Exception {
        String gameId = "g1";
        injectGame(gameId, "alice", "bob");

        aliceHandler.clearMessages();
        bobHandler.clearMessages();
        server.processMove(gameId, "alice", 1, 1);

        Assert.assertTrue(aliceHandler.lastMessageContains("UPDATE"));
        Assert.assertTrue(bobHandler.lastMessageContains("UPDATE"));
    }

    @Test
    public void processMove_wrongPlayer_sendsNotYourTurn() throws Exception {
        String gameId = "g1";
        injectGame(gameId, "alice", "bob");

        bobHandler.clearMessages();
        server.processMove(gameId, "bob", 0, 0); // alice's turn

        Assert.assertTrue(bobHandler.lastMessageContains("ERROR"));
        Assert.assertTrue(bobHandler.lastMessageContains("Not your turn"));
    }

    @Test
    public void processMove_gameNotFound_sendsError() {
        aliceHandler.clearMessages();
        server.processMove("nonexistent", "alice", 1, 1);
        Assert.assertTrue(aliceHandler.lastMessageContains("ERROR"));
        Assert.assertTrue(aliceHandler.lastMessageContains("Game not found"));
    }

    @Test
    public void processMove_notAPlayer_sendsError() throws Exception {
        String gameId = "g1";
        injectGame(gameId, "alice", "bob");
        TestClientHandler carol = new TestClientHandler(createConnectedSocket(), userManager, server, "carol");
        try {
            server.addClient(carol);
            carol.clearMessages();
            server.processMove(gameId, "carol", 1, 1);
            Assert.assertTrue(carol.lastMessageContains("ERROR"));
            Assert.assertTrue(carol.lastMessageContains("Not a player"));
        } finally {
            if (carol != null && carol.getSocket() != null) try { carol.getSocket().close(); } catch (IOException ignored) {}
        }
    }

    @Test
    public void processMove_invalidMove_sendsError() throws Exception {
        String gameId = "g1";
        injectGame(gameId, "alice", "bob");
        server.processMove(gameId, "alice", 0, 0);
        bobHandler.clearMessages();
        server.processMove(gameId, "bob", 0, 0); // already taken
        Assert.assertTrue(bobHandler.lastMessageContains("ERROR"));
        Assert.assertTrue(bobHandler.lastMessageContains("Invalid move"));
    }

    @Test
    public void processMove_win_sendsResultAndUpdatesStats() throws Exception {
        String gameId = "g1";
        injectGame(gameId, "alice", "bob");
        server.processMove(gameId, "alice", 0, 0);
        server.processMove(gameId, "bob", 0, 1);
        server.processMove(gameId, "alice", 1, 0);
        server.processMove(gameId, "bob", 1, 1);
        aliceHandler.clearMessages();
        bobHandler.clearMessages();
        server.processMove(gameId, "alice", 2, 0); // alice wins

        Assert.assertTrue(aliceHandler.lastMessageContains("RESULT"));
        Assert.assertTrue(bobHandler.lastMessageContains("RESULT"));
        Assert.assertEquals("WIN", sessionGetResultFromMessages(aliceHandler.getMessages()));
        Assert.assertEquals("LOSS", sessionGetResultFromMessages(bobHandler.getMessages()));

        Assert.assertEquals(1, userManager.getUser("alice").getWins());
        Assert.assertEquals(1, userManager.getUser("bob").getLosses());
    }

    private String sessionGetResultFromMessages(List<String> msgs) {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Map<String, Object> m = Protocol.parseMessage(msgs.get(i));
            if ("RESULT".equals(m.get("type"))) {
                return (String) m.get("result");
            }
        }
        return null;
    }

    @Test
    public void handleLeaveGame_notifiesOpponentAndUpdatesStats() throws Exception {
        String gameId = "g1";
        injectGame(gameId, "alice", "bob");
        server.processMove(gameId, "alice", 0, 0);
        bobHandler.clearMessages();

        server.handleLeaveGame(gameId, "alice");

        Assert.assertTrue(bobHandler.lastMessageContains("OPPONENT_DISCONNECTED"));
        Assert.assertEquals(1, userManager.getUser("alice").getLosses());
        Assert.assertEquals(1, userManager.getUser("bob").getWins());
    }

    /**
     * Test double that captures sendMessage and allows setting username for addClient.
     */
    static class TestClientHandler extends ClientHandler {
        private final List<String> messages = new CopyOnWriteArrayList<>();
        private Socket socket;

        TestClientHandler(Socket socket, UserManager um, GameServer gs, String usernameForTest) {
            super(socket, um, gs);
            this.socket = socket;
            try {
                Field f = ClientHandler.class.getDeclaredField("username");
                f.setAccessible(true);
                f.set(this, usernameForTest);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void sendMessage(String message) {
            messages.add(message);
        }

        List<String> getMessages() { return messages; }
        void clearMessages() { messages.clear(); }

        boolean lastMessageContains(String sub) {
            if (messages.isEmpty()) return false;
            return messages.get(messages.size() - 1).contains(sub);
        }

        Socket getSocket() { return socket; }
    }
}
