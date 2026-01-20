import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameServer.java - central hub that manage all connection and game session
 * this the brain of game - it accept new client, track who online, manage game, track stat
 * run on port 12345 and accept multiple client connection, each run in separate thread (ClientHandler)
 */
public class GameServer {
    // port where server listen for client connection
    private static final int PORT = 12345;
    // server socket that accept new connection
    private ServerSocket serverSocket;
    // user manager handle account login/register/stat
    private UserManager userManager;
    // rate limiter prevent brute force attack
    private RateLimiter rateLimiter;
    // map username to their connection handler (so we know how to send message to player)
    private Map<String, ClientHandler> clients;
    // map game id to game object (track all active game)
    private Map<String, GameSession> games;
    // track pending challenge: challenger name -> opponent name (waiting for opponent accept/reject)
    private Map<String, String> pendingChallenges;
    // track pending rematch: requester name -> opponent name (waiting for opponent accept/reject)
    private Map<String, String> pendingRematches;
    // remember last opponent so player can easily rematch (player -> last opponent)
    private Map<String, String> lastOpponents;
    
    /**
     * constructor - initialize all data structure and load user from disk
     */
    public GameServer() {
        userManager = new UserManager();
        rateLimiter = new RateLimiter();
        // use concurrent hash map so thread safe when multiple client connect
        clients = new ConcurrentHashMap<>();
        games = new ConcurrentHashMap<>();
        pendingChallenges = new ConcurrentHashMap<>();
        pendingRematches = new ConcurrentHashMap<>();
        lastOpponents = new ConcurrentHashMap<>();
    }
    
    /**
     * getter for rate limiter (used by ClientHandler to check brute force)
     */
    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }
    
    /**
     * start server - listen on port, accept client, create handler for each client
     * this method block forever (until shutdown)
     */
    public void start() {
        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("Game Server started on port " + PORT);
            System.out.println("Waiting for clients...");
            
            // infinite loop: accept client, create handler, let handler run in separate thread
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getRemoteSocketAddress());
                
                // create new handler thread for this client (one thread per client)
                ClientHandler handler = new ClientHandler(clientSocket, userManager, this);
                handler.start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
    
    /**
     * add client to online list after they login. called by ClientHandler after authenticate
     */
    public void addClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            clients.put(handler.getUsername(), handler);
        }
    }
    
    /**
     * remove client from online list when they disconnect. handle case where player disconnect during game
     * if disconnect happen during active game, opponent win automatically
     */
    public void removeClient(ClientHandler handler) {
        if (handler.getUsername() != null) {
            clients.remove(handler.getUsername());
            
            // check if this player in middle of game
            String gameId = findGameByPlayer(handler.getUsername());
            if (gameId != null) {
                GameSession game = games.get(gameId);
                // if game active (not already ended), give opponent win
                if (game != null && !game.isGameOver()) {
                    String disconnectedPlayer = handler.getUsername();
                    // find opponent (other player in same game)
                    String opponent = game.getPlayer1().equals(disconnectedPlayer) 
                        ? game.getPlayer2() : game.getPlayer1();
                    
                    // update stat: disconnect = loss for disconnecter, win for opponent
                    userManager.updateStats(disconnectedPlayer, "LOSS");
                    userManager.updateStats(opponent, "WIN");
                    
                    // remember opponent so they can rematch if want
                    lastOpponents.put(disconnectedPlayer, opponent);
                    lastOpponents.put(opponent, disconnectedPlayer);
                    
                    // notify opponent they won (opponent disconnect)
                    Map<String, Object> msg = new java.util.HashMap<>();
                    msg.put("type", "OPPONENT_DISCONNECTED");
                    msg.put("gameId", gameId);
                    sendToPlayer(opponent, Protocol.createMessage(msg));
                    
                    // remove game from active list
                    games.remove(gameId);
                }
            }
        }
    }
    
    /**
     * send list of all online player to all connected client
     * client use this to show who available to challenge
     */
    public void broadcastPlayerList() {
        // get array of all currently online user
        String[] onlineUsers = userManager.getOnlineUsers();
        // join them with comma separator
        StringBuilder playersList = new StringBuilder();
        for (String user : onlineUsers) {
            if (playersList.length() > 0) {
                playersList.append(",");
            }
            playersList.append(user);
        }
        
        // create message with list
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "PLAYERS_LIST");
        response.put("players", playersList.toString());
        String message = Protocol.createMessage(response);
        
        // send to all authenticated client (everyone see player list)
        for (ClientHandler handler : clients.values()) {
            if (handler.isAuthenticated()) {
                handler.sendMessage(message);
            }
        }
    }
    
    /**
     * send challenge request to opponent. called when challenger want to challenge someone
     * if opponent not online, send error back to challenger
     */
    public void sendChallenge(String opponent, String challenger) {
        // check if opponent online
        ClientHandler opponentHandler = clients.get(opponent);
        if (opponentHandler == null) {
            // opponent not online, tell challenger
            ClientHandler challengerHandler = clients.get(challenger);
            if (challengerHandler != null) {
                challengerHandler.sendMessage(Protocol.createErrorMessage("User not available"));
            }
            return;
        }
        
        // add to pending - waiting for opponent response
        pendingChallenges.put(challenger, opponent);
        
        // send challenge to opponent
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE");
        msg.put("challenger", challenger);
        opponentHandler.sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * handle opponent response to challenge. response either "ACCEPT" or "REJECT"
     */
    public void handleChallengeResponse(String challenger, String opponent, String response) {
        // verify that there a pending challenge from this challenger to this opponent
        if (!pendingChallenges.containsKey(challenger) || 
            !pendingChallenges.get(challenger).equals(opponent)) {
            // no pending challenge, tell opponent
            ClientHandler opponentHandler = clients.get(opponent);
            if (opponentHandler != null) {
                opponentHandler.sendMessage(Protocol.createErrorMessage("No pending challenge"));
            }
            return;
        }
        
        // remove from pending list
        pendingChallenges.remove(challenger);
        
        // get challenger connection so we can send response
        ClientHandler challengerHandler = clients.get(challenger);
        
        // create response message
        Map<String, Object> responseMsg = new java.util.HashMap<>();
        responseMsg.put("type", "CHALLENGE_RESPONSE");
        responseMsg.put("opponent", opponent);
        responseMsg.put("response", response);
        
        // send to challenger
        if (challengerHandler != null) {
            challengerHandler.sendMessage(Protocol.createMessage(responseMsg));
        }
        
        // if opponent accept, start game
        if ("ACCEPT".equals(response)) {
            startGame(challenger, opponent);
        }
    }
    
    /**
     * start game between two player. create game session, send START_GAME message to both
     */
    private void startGame(String player1, String player2) {
        // generate unique id for this game (so can track it)
        String gameId = UUID.randomUUID().toString();
        // create game session with tic-tac-toe board
        GameSession game = new GameSession(gameId, player1, player2);
        // store game in active game map
        games.put(gameId, game);
        
        // create start message with game info
        Map<String, Object> startMsg = new java.util.HashMap<>();
        startMsg.put("type", "START_GAME");
        startMsg.put("gameId", gameId);
        startMsg.put("player1", player1);
        startMsg.put("player2", player2);
        startMsg.put("currentPlayer", player1); // player1 go first
        
        // send to both player
        String message = Protocol.createMessage(startMsg);
        sendToPlayer(player1, message);
        sendToPlayer(player2, message);
    }
    
    /**
     * process move from player. check valid, update board, check if game over
     * send update to both player after move, and send result if game end
     */
    public void processMove(String gameId, String player, int x, int y) {
        // find game
        GameSession game = games.get(gameId);
        if (game == null) {
            sendToPlayer(player, Protocol.createErrorMessage("Game not found"));
            return;
        }
        
        // check that player actually in this game
        if (!game.getPlayer1().equals(player) && !game.getPlayer2().equals(player)) {
            sendToPlayer(player, Protocol.createErrorMessage("Not a player in this game"));
            return;
        }
        
        // check if it player turn (not other player turn)
        if (!game.getCurrentPlayer().equals(player)) {
            sendToPlayer(player, Protocol.createErrorMessage("Not your turn"));
            return;
        }
        
        // try to make move (check if position valid and not occupied)
        if (!game.makeMove(player, x, y)) {
            sendToPlayer(player, Protocol.createErrorMessage("Invalid move, try again"));
            return;
        }
        
        // move valid, create update message with new board state
        Map<String, Object> updateMsg = new java.util.HashMap<>();
        updateMsg.put("type", "UPDATE");
        updateMsg.put("gameId", gameId);
        updateMsg.put("board", game.getBoardMap());
        updateMsg.put("currentPlayer", game.getCurrentPlayer()); // who turn next
        
        // send update to both player so they see board
        String updateMessage = Protocol.createMessage(updateMsg);
        sendToPlayer(game.getPlayer1(), updateMessage);
        sendToPlayer(game.getPlayer2(), updateMessage);
        
        // check if someone win or tie
        if (game.isGameOver()) {
            // game end, determine result for each player (WIN, LOSS, or DRAW)
            String result1 = game.getResultFor(game.getPlayer1());
            String result2 = game.getResultFor(game.getPlayer2());
            
            // update stats in database (write to file)
            userManager.updateStats(game.getPlayer1(), result1);
            userManager.updateStats(game.getPlayer2(), result2);
            
            // send result to player1
            Map<String, Object> resultMsg1 = new java.util.HashMap<>();
            resultMsg1.put("type", "RESULT");
            resultMsg1.put("gameId", gameId);
            resultMsg1.put("result", result1); // "WIN", "LOSS", or "DRAW"
            resultMsg1.put("board", game.getBoardMap()); // final board state
            
            // send result to player2
            Map<String, Object> resultMsg2 = new java.util.HashMap<>();
            resultMsg2.put("type", "RESULT");
            resultMsg2.put("gameId", gameId);
            resultMsg2.put("result", result2);
            resultMsg2.put("board", game.getBoardMap());
            
            // send both result
            sendToPlayer(game.getPlayer1(), Protocol.createMessage(resultMsg1));
            sendToPlayer(game.getPlayer2(), Protocol.createMessage(resultMsg2));
            
            // remember opponent so they can rematch
            lastOpponents.put(game.getPlayer1(), game.getPlayer2());
            lastOpponents.put(game.getPlayer2(), game.getPlayer1());
            
            // remove game from active list (no longer playing)
            games.remove(gameId);
        }
    }
    
    /**
     * handle player leaving game mid-way (e.g., close window, quit button)
     * treat as loss for leaving player, win for opponent
     */
    public void handleLeaveGame(String gameId, String player) {
        // find game
        GameSession game = games.get(gameId);
        if (game == null) {
            return; // game not found or already ended
        }
        
        // check if game still active
        if (!game.isGameOver()) {
            String leavingPlayer = player;
            // find opponent (other player)
            String opponent = game.getPlayer1().equals(leavingPlayer) 
                ? game.getPlayer2() : game.getPlayer1();
            
            // update stat: leaving = loss for leaver, win for opponent
            userManager.updateStats(leavingPlayer, "LOSS");
            userManager.updateStats(opponent, "WIN");
            
            // remember opponent for rematch
            lastOpponents.put(leavingPlayer, opponent);
            lastOpponents.put(opponent, leavingPlayer);
            
            // notify opponent that player left (opponent win)
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("type", "OPPONENT_DISCONNECTED");
            msg.put("gameId", gameId);
            sendToPlayer(opponent, Protocol.createMessage(msg));
            
            // remove game
            games.remove(gameId);
        }
    }
    
    /**
     * send message to player (helper method). find player connection and send
     */
    private void sendToPlayer(String username, String message) {
        // find handler for this player
        ClientHandler handler = clients.get(username);
        if (handler != null) {
            handler.sendMessage(message);
        }
    }
    
    /**
     * find game that specific player playing. return game id or null if not in game
     */
    private String findGameByPlayer(String username) {
        // search through all active game
        for (Map.Entry<String, GameSession> entry : games.entrySet()) {
            GameSession game = entry.getValue();
            // check if player is player1 or player2 in this game
            if (game.getPlayer1().equals(username) || game.getPlayer2().equals(username)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    /**
     * getter for last opponent (used when player want rematch with last opponent)
     */
    public String getLastOpponent(String username) {
        return lastOpponents.get(username);
    }
    
    /**
     * send rematch request to opponent. called when player want rematch with last opponent
     * if opponent not online, send error back to requester
     */
    public void sendRematchRequest(String opponent, String requester) {
        // check if opponent online
        ClientHandler opponentHandler = clients.get(opponent);
        if (opponentHandler == null) {
            // opponent offline, tell requester
            ClientHandler requesterHandler = clients.get(requester);
            if (requesterHandler != null) {
                requesterHandler.sendMessage(Protocol.createErrorMessage("User not available"));
            }
            return;
        }
        
        // add to pending - waiting for opponent response
        pendingRematches.put(requester, opponent);
        
        // send rematch request to opponent
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_REQUEST");
        msg.put("requester", requester);
        opponentHandler.sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * handle opponent response to rematch request. response either "ACCEPT" or "REJECT"
     */
    public void handleRematchResponse(String requester, String opponent, String response) {
        // verify that there a pending rematch request
        if (!pendingRematches.containsKey(requester) || 
            !pendingRematches.get(requester).equals(opponent)) {
            // no pending rematch, tell opponent
            ClientHandler opponentHandler = clients.get(opponent);
            if (opponentHandler != null) {
                opponentHandler.sendMessage(Protocol.createErrorMessage("No pending rematch"));
            }
            return;
        }
        
        // remove from pending list
        pendingRematches.remove(requester);
        
        // get requester connection
        ClientHandler requesterHandler = clients.get(requester);
        
        // create response message
        Map<String, Object> responseMsg = new java.util.HashMap<>();
        responseMsg.put("type", "REMATCH_RESPONSE");
        responseMsg.put("opponent", opponent);
        responseMsg.put("response", response);
        
        // send to requester
        if (requesterHandler != null) {
            requesterHandler.sendMessage(Protocol.createMessage(responseMsg));
        }
        
        // if opponent accept, start new game
        if ("ACCEPT".equals(response)) {
            startGame(requester, opponent);
        }
    }
    
    /**
     * get leaderboard (top 10 player by wins) and send to client
     */
    public void sendLeaderboard(ClientHandler handler) {
        // get top 10 players sorted by wins then total games
        java.util.List<UserManager.User> leaderboard = userManager.getLeaderboard(10);
        
        // format as string: rank,username,wins,losses,draws|rank,username,wins,losses,draws|...
        StringBuilder leaderboardStr = new StringBuilder();
        int rank = 1;
        for (UserManager.User user : leaderboard) {
            if (leaderboardStr.length() > 0) {
                leaderboardStr.append("|"); // separator between player
            }
            leaderboardStr.append(rank).append(",")
                         .append(user.getUsername()).append(",")
                         .append(user.getWins()).append(",")
                         .append(user.getLosses()).append(",")
                         .append(user.getDraws());
            rank++;
        }
        
        // create message
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LEADERBOARD");
        msg.put("data", leaderboardStr.toString());
        // send to this client
        handler.sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * main - create server instance and start listening for client
     */
    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}

