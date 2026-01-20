import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;

/**
 * ClientHandler.java - Handle each client connection in a separate thread
 * this class run in own thread and manage one connected client. it receive messages from client,
 * check what type of message it is, and then handle it appropriately. it also send response back to client.
 * this way server can handle many clients at same time without blocking
 */
public class ClientHandler extends Thread {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private UserManager userManager;
    private GameServer server;
    private String username;
    private String sessionId;
    private boolean authenticated;
    private RateLimiter rateLimiter;
    private String clientAddress;
    
    public ClientHandler(Socket socket, UserManager userManager, GameServer server) {
        this.socket = socket;
        this.userManager = userManager;
        this.server = server;
        // create unique session id for this connection so we can track it
        this.sessionId = UUID.randomUUID().toString();
        this.authenticated = false;
        this.rateLimiter = server.getRateLimiter();
        // get the address of the client so we can use it for rate limiting
        this.clientAddress = socket.getRemoteSocketAddress().toString();
        
        try {
            // set up input and output stream so we can receive and send message to client
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("Error setting up client handler: " + e.getMessage());
        }
    }
    
    @Override
    public void run() {
        try {
            String message;
            // keep reading message from client until client close connection or something go wrong
            while ((message = in.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + (username != null ? username : "unknown"));
        } finally {
            // make sure we clean up when client disconnect
            cleanup();
        }
    }
    
    /**
     * Handle incoming messages from client. first we parse message into map,
     * then check message type, and call right handler method for it
     */
    private void handleMessage(String message) {
        // convert json message string into map so we can easily access fields
        Map<String, Object> msg = Protocol.parseMessage(message);
        String type = (String) msg.get("type");
        
        // if message not have type field, it invalid so we send error back
        if (type == null) {
            sendMessage(Protocol.createErrorMessage("Invalid message format"));
            return;
        }
        
        // check what type of message it is and call appropriate handler
        switch (type) {
            // user want to create new account - handle registration
            case "REGISTER":
                handleRegister(msg);
                break;
            // user want to login with username and password
            case "LOGIN":
                handleLogin(msg);
                break;
            // user want to see list of online player they can challenge
            case "LIST_PLAYERS":
                handleListPlayers();
                break;
            // user want to send challenge to another player
            case "CHALLENGE":
                handleChallenge(msg);
                break;
            // user respond to incoming challenge (accept or reject)
            case "CHALLENGE_RESPONSE":
                handleChallengeResponse(msg);
                break;
            // user make move during game (send x,y coordinate)
            case "MOVE":
                handleMove(msg);
                break;
            // user want to logout and disconnect
            case "LOGOUT":
                handleLogout();
                break;
            // user want rematch with previous opponent
            case "REMATCH_REQUEST":
                handleRematchRequest(msg);
                break;
            // user respond to rematch request (accept or reject)
            case "REMATCH_RESPONSE":
                handleRematchResponse(msg);
                break;
            // user want to see leaderboard of top player
            case "LEADERBOARD":
                handleLeaderboard();
                break;
            // user leave game early (close window or go back to lobby)
            case "LEAVE_GAME":
                handleLeaveGame(msg);
                break;
            // if message type not recognized, send error
            default:
                sendMessage(Protocol.createErrorMessage("Unknown message type"));
        }
    }
    
    private void handleRegister(Map<String, Object> msg) {
        try {
        String username = (String) msg.get("username");
        String password = (String) msg.get("password");
            String name = (String) msg.get("name");
            String email = (String) msg.get("email");
        
        // check if user provide username and password
        if (username == null || password == null) {
            sendMessage(Protocol.createErrorMessage("Username and password required"));
            return;
        }
        
            // validate and clean user input so it not contain bad stuff that could break system
            try {
                username = SecurityUtils.validateUsername(username);
                password = SecurityUtils.validatePassword(password);
                name = SecurityUtils.validateName(name);
                email = SecurityUtils.validateEmail(email);
            } catch (IllegalArgumentException e) {
                sendMessage(Protocol.createErrorMessage(e.getMessage()));
                return;
            }
            
            // try to register user. if username already exist it return false
            if (userManager.register(username, password, name, email)) {
            sendMessage(Protocol.createSuccessMessage("Registration successful"));
        } else {
            sendMessage(Protocol.createErrorMessage("Username already exists"));
            }
        } catch (Exception e) {
            sendMessage(Protocol.createErrorMessage("Registration failed: " + e.getMessage()));
        }
    }
    
    private void handleLogin(Map<String, Object> msg) {
        String username = (String) msg.get("username");
        String password = (String) msg.get("password");
        
        // check if user provide both username and password
        if (username == null || password == null) {
            sendMessage(Protocol.createErrorMessage("Username and password required"));
            return;
        }
        
        // clean the input to remove any dangerous character
        try {
            username = SecurityUtils.sanitize(username);
            password = SecurityUtils.sanitize(password);
        } catch (Exception e) {
            sendMessage(Protocol.createErrorMessage("Invalid input format"));
            return;
        }
        
        // check if this client is trying too many time to login. if yes, block it for a while
        String rateLimitKey = clientAddress + ":" + username;
        if (rateLimiter.isRateLimited(rateLimitKey)) {
            long remaining = rateLimiter.getRemainingLockoutTime(rateLimitKey);
            sendMessage(Protocol.createErrorMessage("Too many failed attempts. Please try again in " + 
                remaining + " seconds."));
            return;
        }
        
        // try to authenticate user with provided username and password
        if (userManager.login(username, password)) {
            // check if user already logged in from other place
            if (userManager.isOnline(username)) {
                sendMessage(Protocol.createErrorMessage("User already logged in"));
                return;
            }
            
            // login success so we clear the rate limit counter for this user
            rateLimiter.recordSuccess(rateLimitKey);
            
            // now this handler is authenticated and associated with username
            this.username = username;
            this.authenticated = true;
            userManager.setOnline(username, sessionId);
            server.addClient(this);
            
            // prepare response with user information
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("type", "LOGIN_SUCCESS");
            response.put("username", username);
            UserManager.User user = userManager.getUser(username);
            response.put("wins", String.valueOf(user.getWins()));
            response.put("losses", String.valueOf(user.getLosses()));
            response.put("draws", String.valueOf(user.getDraws()));
            response.put("name", user.getName() != null ? user.getName() : "");
            response.put("email", user.getEmail() != null ? user.getEmail() : "");
            // we not send password back for security reason - user not need it and no one else should see it
            sendMessage(Protocol.createMessage(response));
            
            // tell all other online player about this new player so they can see it in their list
            server.broadcastPlayerList();
        } else {
            // login failed so we record this failed attempt for rate limiting
            rateLimiter.recordFailedAttempt(rateLimitKey);
            sendMessage(Protocol.createErrorMessage("Incorrect credentials"));
        }
    }
    
    private void handleListPlayers() {
        // user must be logged in before they can see other player
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // get list of all online user
        String[] onlineUsers = userManager.getOnlineUsers();
        StringBuilder playersList = new StringBuilder();
        // go through all online user and add them to list, but skip current user
        for (String user : onlineUsers) {
            if (!user.equals(username)) {
                if (playersList.length() > 0) {
                    playersList.append(",");
                }
                playersList.append(user);
            }
        }
        
        // send list of player back to client
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "PLAYERS_LIST");
        response.put("players", playersList.toString());
        sendMessage(Protocol.createMessage(response));
    }
    
    private void handleChallenge(Map<String, Object> msg) {
        // user must be logged in to challenge someone
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // get the username of opponent we want to challenge
        String opponent = (String) msg.get("opponent");
        if (opponent == null) {
            sendMessage(Protocol.createErrorMessage("Opponent username required"));
            return;
        }
        
        // check if opponent is actually online
        if (!userManager.isOnline(opponent)) {
            sendMessage(Protocol.createErrorMessage("User not available"));
            return;
        }
        
        // make sure user not try to challenge themselves
        if (opponent.equals(username)) {
            sendMessage(Protocol.createErrorMessage("Cannot challenge yourself"));
            return;
        }
        
        // send challenge to opponent through server
        server.sendChallenge(opponent, username);
    }
    
    private void handleChallengeResponse(Map<String, Object> msg) {
        // user must be logged in to respond to challenge
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // get who challenge us and what is our response (accept or reject)
        String challenger = (String) msg.get("challenger");
        String response = (String) msg.get("response"); // "ACCEPT" or "REJECT"
        
        if (challenger == null || response == null) {
            sendMessage(Protocol.createErrorMessage("Invalid challenge response"));
            return;
        }
        
        // send the response to server so it can handle it
        server.handleChallengeResponse(challenger, username, response);
    }
    
    private void handleMove(Map<String, Object> msg) {
        // user must be logged in to make move in game
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // get game id and move data
        String gameId = (String) msg.get("gameId");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) msg.get("data");
        
        if (gameId == null || data == null) {
            sendMessage(Protocol.createErrorMessage("Invalid move format"));
            return;
        }
        
        // get x and y coordinate from move data
        String xStr = data.get("x");
        String yStr = data.get("y");
        
        if (xStr == null || yStr == null) {
            sendMessage(Protocol.createErrorMessage("Move coordinates required"));
            return;
        }
        
        try {
            // convert string coordinate to integer
            int x = Integer.parseInt(xStr);
            int y = Integer.parseInt(yStr);
            // send move to server to process it
            server.processMove(gameId, username, x, y);
        } catch (NumberFormatException e) {
            sendMessage(Protocol.createErrorMessage("Invalid move coordinates"));
        }
    }
    
    private void handleLogout() {
        // clean up and disconnect
        cleanup();
    }
    
    private void handleRematchRequest(Map<String, Object> msg) {
        // user must be logged in to request rematch
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // get opponent for rematch
        String opponent = (String) msg.get("opponent");
        if (opponent == null) {
            // if no opponent given, try to get last opponent from server
            opponent = server.getLastOpponent(username);
            if (opponent == null) {
                sendMessage(Protocol.createErrorMessage("No previous opponent found"));
                return;
            }
        }
        
        // check if opponent is online
        if (!userManager.isOnline(opponent)) {
            sendMessage(Protocol.createErrorMessage("User not available"));
            return;
        }
        
        // send rematch request to opponent
        server.sendRematchRequest(opponent, username);
    }
    
    private void handleRematchResponse(Map<String, Object> msg) {
        // user must be logged in to respond to rematch
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // get who requested rematch and what is response
        String opponent = (String) msg.get("opponent");
        String response = (String) msg.get("response"); // "ACCEPT" or "REJECT"
        
        if (opponent == null || response == null) {
            sendMessage(Protocol.createErrorMessage("Invalid rematch response"));
            return;
        }
        
        // tell server about response
        server.handleRematchResponse(opponent, username, response);
    }
    
    private void handleLeaderboard() {
        // user must be logged in to see leaderboard
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // ask server to send leaderboard to this client
        server.sendLeaderboard(this);
    }
    
    private void handleLeaveGame(Map<String, Object> msg) {
        // user must be logged in to leave game
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        // get game id
        String gameId = (String) msg.get("gameId");
        if (gameId == null) {
            sendMessage(Protocol.createErrorMessage("Game ID required"));
            return;
        }
        
        // tell server that player leave the game
        server.handleLeaveGame(gameId, username);
    }
    
    // send message to this client
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    // return username of client connected to this handler
    public String getUsername() {
        return username;
    }
    
    // return unique session id for this connection
    public String getSessionId() {
        return sessionId;
    }
    
    // check if client already authenticated
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    // cleanup when client disconnect or logout
    private void cleanup() {
        // mark user as offline if they was logged in
        if (username != null) {
            userManager.setOffline(username);
            server.removeClient(this);
            server.broadcastPlayerList();
        }
        
        try {
            // close socket connection
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }
}

