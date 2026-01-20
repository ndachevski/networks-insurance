import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameServer.java - Main server that manages connections and game sessions
 */
public class GameServer {
    private static final int PORT = 12345;
    private ServerSocket serverSocket;
    private UserManager userManager;
    private RateLimiter rateLimiter;
    private Map<String, ClientHandler> clients; // username -> handler
    private Map<String, GameSession> games; // gameId -> game
    private Map<String, String> pendingChallenges; // challenger -> opponent
    private Map<String, String> pendingRematches; // requester -> opponent
    private Map<String, String> lastOpponents; // player -> last opponent
    
    public GameServer() {
        userManager = new UserManager();
        rateLimiter = new RateLimiter();
        clients = new ConcurrentHashMap<>();
        games = new ConcurrentHashMap<>();
        pendingChallenges = new ConcurrentHashMap<>();
        pendingRematches = new ConcurrentHashMap<>();
        lastOpponents = new ConcurrentHashMap<>();
    }
    
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }
    
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Game Server started on port " + PORT);
            System.out.println("Waiting for clients...");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                
                ClientHandler handler = new ClientHandler(clientSocket, userManager, this);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
    
    public void addClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            clients.put(handler.getUsername(), handler);
        }
    }
    
    public void removeClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            clients.remove(handler.getUsername());
            
            // Handle disconnection during game
            String gameId = findGameByPlayer(handler.getUsername());
            if (gameId != null) {
                GameSession game = games.get(gameId);
                if (game != null && !game.isGameOver()) {
                    String disconnectedPlayer = handler.getUsername();
                    String opponent = game.getPlayer1().equals(disconnectedPlayer) 
                        ? game.getPlayer2() : game.getPlayer1();
                    
                    // Update statistics: disconnected player gets loss, opponent gets win
                    userManager.updateStats(disconnectedPlayer, "LOSS");
                    userManager.updateStats(opponent, "WIN");
                    
                    // Store last opponents for rematch
                    lastOpponents.put(disconnectedPlayer, opponent);
                    lastOpponents.put(opponent, disconnectedPlayer);
                    
                    Map<String, Object> msg = new java.util.HashMap<>();
                    msg.put("type", "OPPONENT_DISCONNECTED");
                    msg.put("gameId", gameId);
                    sendToPlayer(opponent, Protocol.createMessage(msg));
                    
                    games.remove(gameId);
                }
            }
        }
    }
    
    public void broadcastPlayerList() {
        String[] onlineUsers = userManager.getOnlineUsers();
        StringBuilder playersList = new StringBuilder();
        for (String user : onlineUsers) {
            if (playersList.length() > 0) {
                playersList.append(",");
            }
            playersList.append(user);
        }
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "PLAYERS_LIST");
        response.put("players", playersList.toString());
        String message = Protocol.createMessage(response);
        
        for (ClientHandler handler : clients.values()) {
            if (handler.isAuthenticated()) {
                handler.sendMessage(message);
            }
        }
    }
    
    public void sendChallenge(String opponent, String challenger) {
        ClientHandler opponentHandler = clients.get(opponent);
        if (opponentHandler == null) {
            ClientHandler challengerHandler = clients.get(challenger);
            if (challengerHandler != null) {
                challengerHandler.sendMessage(Protocol.createErrorMessage("User not available"));
            }
            return;
        }
        
        pendingChallenges.put(challenger, opponent);
        
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE");
        msg.put("challenger", challenger);
        opponentHandler.sendMessage(Protocol.createMessage(msg));
    }
    
    public void handleChallengeResponse(String challenger, String opponent, String response) {
        if (!pendingChallenges.containsKey(challenger) || 
            !pendingChallenges.get(challenger).equals(opponent)) {
            ClientHandler opponentHandler = clients.get(opponent);
            if (opponentHandler != null) {
                opponentHandler.sendMessage(Protocol.createErrorMessage("No pending challenge"));
            }
            return;
        }
        
        pendingChallenges.remove(challenger);
        
        ClientHandler challengerHandler = clients.get(challenger);
        
        Map<String, Object> responseMsg = new java.util.HashMap<>();
        responseMsg.put("type", "CHALLENGE_RESPONSE");
        responseMsg.put("opponent", opponent);
        responseMsg.put("response", response);
        
        if (challengerHandler != null) {
            challengerHandler.sendMessage(Protocol.createMessage(responseMsg));
        }
        
        if ("ACCEPT".equals(response)) {
            startGame(challenger, opponent);
        }
    }
    
    private void startGame(String player1, String player2) {
        String gameId = UUID.randomUUID().toString();
        GameSession game = new GameSession(gameId, player1, player2);
        games.put(gameId, game);
        
        Map<String, Object> startMsg = new java.util.HashMap<>();
        startMsg.put("type", "START_GAME");
        startMsg.put("gameId", gameId);
        startMsg.put("player1", player1);
        startMsg.put("player2", player2);
        startMsg.put("currentPlayer", player1);
        
        String message = Protocol.createMessage(startMsg);
        sendToPlayer(player1, message);
        sendToPlayer(player2, message);
    }
    
    public void processMove(String gameId, String player, int x, int y) {
        GameSession game = games.get(gameId);
        if (game == null) {
            sendToPlayer(player, Protocol.createErrorMessage("Game not found"));
            return;
        }
        
        if (!game.getPlayer1().equals(player) && !game.getPlayer2().equals(player)) {
            sendToPlayer(player, Protocol.createErrorMessage("Not a player in this game"));
            return;
        }
        
        if (!game.getCurrentPlayer().equals(player)) {
            sendToPlayer(player, Protocol.createErrorMessage("Not your turn"));
            return;
        }
        
        if (!game.makeMove(player, x, y)) {
            sendToPlayer(player, Protocol.createErrorMessage("Invalid move, try again"));
            return;
        }
        
        // Send update to both players
        Map<String, Object> updateMsg = new java.util.HashMap<>();
        updateMsg.put("type", "UPDATE");
        updateMsg.put("gameId", gameId);
        updateMsg.put("board", game.getBoardMap());
        updateMsg.put("currentPlayer", game.getCurrentPlayer());
        
        String updateMessage = Protocol.createMessage(updateMsg);
        sendToPlayer(game.getPlayer1(), updateMessage);
        sendToPlayer(game.getPlayer2(), updateMessage);
        
        // Check if game is over
        if (game.isGameOver()) {
            String result1 = game.getResultFor(game.getPlayer1());
            String result2 = game.getResultFor(game.getPlayer2());
            
            // Update statistics
            userManager.updateStats(game.getPlayer1(), result1);
            userManager.updateStats(game.getPlayer2(), result2);
            
            // Send results
            Map<String, Object> resultMsg1 = new java.util.HashMap<>();
            resultMsg1.put("type", "RESULT");
            resultMsg1.put("gameId", gameId);
            resultMsg1.put("result", result1);
            resultMsg1.put("board", game.getBoardMap());
            
            Map<String, Object> resultMsg2 = new java.util.HashMap<>();
            resultMsg2.put("type", "RESULT");
            resultMsg2.put("gameId", gameId);
            resultMsg2.put("result", result2);
            resultMsg2.put("board", game.getBoardMap());
            
            sendToPlayer(game.getPlayer1(), Protocol.createMessage(resultMsg1));
            sendToPlayer(game.getPlayer2(), Protocol.createMessage(resultMsg2));
            
            // Store last opponents for rematch
            lastOpponents.put(game.getPlayer1(), game.getPlayer2());
            lastOpponents.put(game.getPlayer2(), game.getPlayer1());
            
            games.remove(gameId);
        }
    }
    
    /**
     * Handle player leaving game (e.g., closing game window)
     */
    public void handleLeaveGame(String gameId, String player) {
        GameSession game = games.get(gameId);
        if (game == null) {
            return; // Game doesn't exist or already ended
        }
        
        if (!game.isGameOver()) {
            String leavingPlayer = player;
            String opponent = game.getPlayer1().equals(leavingPlayer) 
                ? game.getPlayer2() : game.getPlayer1();
            
            // Update statistics: leaving player gets loss, opponent gets win
            userManager.updateStats(leavingPlayer, "LOSS");
            userManager.updateStats(opponent, "WIN");
            
            // Store last opponents for rematch
            lastOpponents.put(leavingPlayer, opponent);
            lastOpponents.put(opponent, leavingPlayer);
            
            // Notify opponent
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("type", "OPPONENT_DISCONNECTED");
            msg.put("gameId", gameId);
            sendToPlayer(opponent, Protocol.createMessage(msg));
            
            // Remove game
            games.remove(gameId);
        }
    }
    
    private void sendToPlayer(String username, String message) {
        ClientHandler handler = clients.get(username);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }
    
    private String findGameByPlayer(String username) {
        for (Map.Entry<String, GameSession> entry : games.entrySet()) {
            GameSession game = entry.getValue();
            if (game.getPlayer1().equals(username) || game.getPlayer2().equals(username)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    public String getLastOpponent(String username) {
        return lastOpponents.get(username);
    }
    
    public void sendRematchRequest(String opponent, String requester) {
        ClientHandler opponentHandler = clients.get(opponent);
        if (opponentHandler == null) {
            ClientHandler requesterHandler = clients.get(requester);
            if (requesterHandler != null) {
                requesterHandler.sendMessage(Protocol.createErrorMessage("User not available"));
            }
            return;
        }
        
        pendingRematches.put(requester, opponent);
        
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_REQUEST");
        msg.put("requester", requester);
        opponentHandler.sendMessage(Protocol.createMessage(msg));
    }
    
    public void handleRematchResponse(String requester, String opponent, String response) {
        if (!pendingRematches.containsKey(requester) || 
            !pendingRematches.get(requester).equals(opponent)) {
            ClientHandler opponentHandler = clients.get(opponent);
            if (opponentHandler != null) {
                opponentHandler.sendMessage(Protocol.createErrorMessage("No pending rematch"));
            }
            return;
        }
        
        pendingRematches.remove(requester);
        
        ClientHandler requesterHandler = clients.get(requester);
        
        Map<String, Object> responseMsg = new java.util.HashMap<>();
        responseMsg.put("type", "REMATCH_RESPONSE");
        responseMsg.put("opponent", opponent);
        responseMsg.put("response", response);
        
        if (requesterHandler != null) {
            requesterHandler.sendMessage(Protocol.createMessage(responseMsg));
        }
        
        if ("ACCEPT".equals(response)) {
            startGame(requester, opponent);
        }
    }
    
    public void sendLeaderboard(ClientHandler handler) {
        java.util.List<UserManager.User> leaderboard = userManager.getLeaderboard(10);
        
        StringBuilder leaderboardStr = new StringBuilder();
        int rank = 1;
        for (UserManager.User user : leaderboard) {
            if (leaderboardStr.length() > 0) {
                leaderboardStr.append("|");
            }
            leaderboardStr.append(rank).append(",")
                         .append(user.getUsername()).append(",")
                         .append(user.getWins()).append(",")
                         .append(user.getLosses()).append(",")
                         .append(user.getDraws());
            rank++;
        }
        
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LEADERBOARD");
        msg.put("data", leaderboardStr.toString());
        handler.sendMessage(Protocol.createMessage(msg));
    }
    
    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}

