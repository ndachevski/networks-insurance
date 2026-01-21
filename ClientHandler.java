// Students: CSY23102, CSY23052, CSY23031

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;

/**
 * ClientHandler runs in its own thread and manages the server-side connection for one client.
 * Receives messages from the client, parses them, routes them to appropriate handlers, and sends responses.
 * This threading model allows the server to handle multiple clients simultaneously without blocking.
 * All state transitions (unauthenticated → authenticated → in game) happen in this thread sequentially.
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
    // track client ip for rate limiting - prevents timing out legitimate users when one ip tries brute force
    private String clientAddress;
    
    public ClientHandler(Socket socket, UserManager userManager, GameServer server) {
        this.socket = socket;
        this.userManager = userManager;
        this.server = server;
        // unique session id for this connection allows tracking it across requests
        this.sessionId = UUID.randomUUID().toString();
        this.authenticated = false;
        this.rateLimiter = server.getRateLimiter();
        // extract client ip for rate limiting
        this.clientAddress = socket.getRemoteSocketAddress().toString();
        
        try {
            // set up input/output streams for message exchange
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
            // read messages from client until connection closes or error occurs
            // each message is processed sequentially, maintaining order and state
            while ((message = in.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + (username != null ? username : "unknown"));
        } finally {
            // ensure we clean up resources when connection ends
            cleanup();
        }
    }
    
    /**
     * parse incoming message and route to appropriate handler based on message type
     */
    private void handleMessage(String message) {
        // parse the incoming message to extract the type and data fields
        Map<String, Object> msg = Protocol.parseMessage(message);
        String type = (String) msg.get("type");
        
        if (type == null) {
            sendMessage(Protocol.createErrorMessage("Invalid message format"));
            return;
        }
        
        // dispatch to appropriate handler for this message type. this design ensures
        // requests are processed sequentially, preventing race conditions where state
        // could be corrupted by concurrent modifications
        switch (type) {
            case "REGISTER":
                handleRegister(msg);
                break;
            case "LOGIN":
                handleLogin(msg);
                break;
            case "LIST_PLAYERS":
                handleListPlayers();
                break;
            case "CHALLENGE":
                handleChallenge(msg);
                break;
            case "CHALLENGE_RESPONSE":
                handleChallengeResponse(msg);
                break;
            case "MOVE":
                handleMove(msg);
                break;
            case "LOGOUT":
                handleLogout();
                break;
            case "REMATCH_REQUEST":
                handleRematchRequest(msg);
                break;
            case "REMATCH_RESPONSE":
                handleRematchResponse(msg);
                break;
            case "LEADERBOARD":
                handleLeaderboard();
                break;
            case "LEAVE_GAME":
                handleLeaveGame(msg);
                break;
            default:
                sendMessage(Protocol.createErrorMessage("Unknown message type"));
        }
    }
    
    /**
     * handle registration request - validate inputs, check username availability,
     * hash password before storing
     */
    private void handleRegister(Map<String, Object> msg) {
        try {
            String username = (String) msg.get("username");
            String password = (String) msg.get("password");
            String name = (String) msg.get("name");
            String email = (String) msg.get("email");
        
            if (username == null || password == null) {
                sendMessage(Protocol.createErrorMessage("Username and password required"));
                return;
            }
        
            // validate and sanitize inputs to prevent injection and enforce constraints
            try {
                username = SecurityUtils.validateUsername(username);
                password = SecurityUtils.validatePassword(password);
                name = SecurityUtils.validateName(name);
                email = SecurityUtils.validateEmail(email);
            } catch (IllegalArgumentException e) {
                sendMessage(Protocol.createErrorMessage(e.getMessage()));
                return;
            }
            
            if (userManager.register(username, password, name, email)) {
                sendMessage(Protocol.createSuccessMessage("Registration successful"));
            } else {
                sendMessage(Protocol.createErrorMessage("Username already exists"));
            }
        } catch (Exception e) {
            sendMessage(Protocol.createErrorMessage("Registration failed: " + e.getMessage()));
        }
    }
    
    /**
     * handle login - validate credentials, check rate limiting, update online status,
     * and send back player stats. passwords are hashed so never sent back to client
     */
    private void handleLogin(Map<String, Object> msg) {
        String username = (String) msg.get("username");
        String password = (String) msg.get("password");
        
        if (username == null || password == null) {
            sendMessage(Protocol.createErrorMessage("Username and password required"));
            return;
        }
        
        // sanitize inputs before using them
        try {
            username = SecurityUtils.sanitize(username);
            password = SecurityUtils.sanitize(password);
        } catch (Exception e) {
            sendMessage(Protocol.createErrorMessage("Invalid input format"));
            return;
        }
        
        // rate limit by ip + username combination to prevent brute force
        String rateLimitKey = clientAddress + ":" + username;
        if (rateLimiter.isRateLimited(rateLimitKey)) {
            long remaining = rateLimiter.getRemainingLockoutTime(rateLimitKey);
            sendMessage(Protocol.createErrorMessage("Too many failed attempts. Please try again in " + 
                remaining + " seconds."));
            return;
        }
        
        // attempt authentication
        if (userManager.login(username, password)) {
            // reject login if this user is already connected elsewhere
            if (userManager.isOnline(username)) {
                sendMessage(Protocol.createErrorMessage("User already logged in"));
                return;
            }
            
            // clear rate limit counter on successful login
            rateLimiter.recordSuccess(rateLimitKey);
            
            // mark this connection as authenticated
            this.username = username;
            this.authenticated = true;
            userManager.setOnline(username, sessionId);
            server.addClient(this);
            
            // send client their profile and stats
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("type", "LOGIN_SUCCESS");
            response.put("username", username);
            UserManager.User user = userManager.getUser(username);
            response.put("wins", String.valueOf(user.getWins()));
            response.put("losses", String.valueOf(user.getLosses()));
            response.put("draws", String.valueOf(user.getDraws()));
            response.put("name", user.getName() != null ? user.getName() : "");
            response.put("email", user.getEmail() != null ? user.getEmail() : "");
            // DO NOT send password back for security
            sendMessage(Protocol.createMessage(response));
            
            // notify other connected clients of updated player list
            server.broadcastPlayerList();
        } else {
            // record failed attempt for rate limiting
            rateLimiter.recordFailedAttempt(rateLimitKey);
            sendMessage(Protocol.createErrorMessage("Incorrect credentials"));
        }
    }
    
    private void handleListPlayers() {
        // require authentication to prevent unauthorized player enumeration
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String[] onlineUsers = userManager.getOnlineUsers();
        StringBuilder playersList = new StringBuilder();
        // exclude ourselves from the list since we can't challenge ourselves
        for (String user : onlineUsers) {
            if (!user.equals(username)) {
                if (playersList.length() > 0) {
                    playersList.append(",");
                }
                playersList.append(user);
            }
        }
        
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("type", "PLAYERS_LIST");
        response.put("players", playersList.toString());
        sendMessage(Protocol.createMessage(response));
    }
    
    /**
     * initiate a challenge - validate opponent exists and is available. delegates to gameserver
     * which enforces the one-game-per-player constraint
     */
    private void handleChallenge(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String opponent = (String) msg.get("opponent");
        if (opponent == null) {
            sendMessage(Protocol.createErrorMessage("Opponent username required"));
            return;
        }
        
        if (!userManager.isOnline(opponent)) {
            sendMessage(Protocol.createErrorMessage("User not available"));
            return;
        }
        
        if (opponent.equals(username)) {
            sendMessage(Protocol.createErrorMessage("Cannot challenge yourself"));
            return;
        }
        
        server.sendChallenge(opponent, username);
    }
    
    /**
     * respond to an incoming challenge. validate challenge exists and route to server
     */
    private void handleChallengeResponse(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String challenger = (String) msg.get("challenger");
        String response = (String) msg.get("response");
        
        if (challenger == null || response == null) {
            sendMessage(Protocol.createErrorMessage("Invalid challenge response"));
            return;
        }
        
        server.handleChallengeResponse(challenger, username, response);
    }
    
    /**
     * handle a move attempt - extract coordinates and pass to server for validation and processing
     */
    private void handleMove(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String gameId = (String) msg.get("gameId");
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) msg.get("data");
        
        if (gameId == null || data == null) {
            sendMessage(Protocol.createErrorMessage("Invalid move format"));
            return;
        }
        
        String xStr = data.get("x");
        String yStr = data.get("y");
        
        if (xStr == null || yStr == null) {
            sendMessage(Protocol.createErrorMessage("Move coordinates required"));
            return;
        }
        
        try {
            int x = Integer.parseInt(xStr);
            int y = Integer.parseInt(yStr);
            server.processMove(gameId, username, x, y);
        } catch (NumberFormatException e) {
            sendMessage(Protocol.createErrorMessage("Invalid move coordinates"));
        }
    }
    
    private void handleLogout() {
        // closing the connection triggers cleanup which handles the rest
        cleanup();
    }
    
    /**
     * handle rematch request - get last opponent from server or from message parameter.
     * rematch is only allowed against the same person we just played to prevent abuse
     */
    private void handleRematchRequest(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String opponent = (String) msg.get("opponent");
        if (opponent == null) {
            // try to get last opponent from server if not specified
            opponent = server.getLastOpponent(username);
            if (opponent == null) {
                sendMessage(Protocol.createErrorMessage("No previous opponent found"));
                return;
            }
        }
        
        if (!userManager.isOnline(opponent)) {
            sendMessage(Protocol.createErrorMessage("User not available"));
            return;
        }
        
        server.sendRematchRequest(opponent, username);
    }
    
    /**
     * respond to rematch request
     */
    private void handleRematchResponse(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String opponent = (String) msg.get("opponent");
        String response = (String) msg.get("response");
        
        if (opponent == null || response == null) {
            sendMessage(Protocol.createErrorMessage("Invalid rematch response"));
            return;
        }
        
        server.handleRematchResponse(opponent, username, response);
    }
    
    /**
     * send leaderboard to client
     */
    private void handleLeaderboard() {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        server.sendLeaderboard(this);
    }
    
    /**
     * handle player leaving an active game (treat as forfeit)
     */
    private void handleLeaveGame(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String gameId = (String) msg.get("gameId");
        if (gameId == null) {
            sendMessage(Protocol.createErrorMessage("Game ID required"));
            return;
        }
        
        server.handleLeaveGame(gameId, username);
    }
    
    /**
     * send message to client - used for all responses and notifications
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getSessionId() {
        return sessionId;
    }
    
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    /**
     * cleanup connection - mark offline, remove from server, close socket
     */
    private void cleanup() {
        if (username != null) {
            userManager.setOffline(username);
            server.removeClient(this);
            server.broadcastPlayerList();
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }
}
