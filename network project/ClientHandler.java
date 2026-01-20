import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.UUID;

/**
 * ClientHandler.java - Handles each client connection in a separate thread
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
        this.sessionId = UUID.randomUUID().toString();
        this.authenticated = false;
        this.rateLimiter = server.getRateLimiter();
        this.clientAddress = socket.getRemoteSocketAddress().toString();
        
        try {
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
            while ((message = in.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Client disconnected: " + (username != null ? username : "unknown"));
        } finally {
            cleanup();
        }
    }
    
    /**
     * Handle incoming messages from client
     */
    private void handleMessage(String message) {
        Map<String, Object> msg = Protocol.parseMessage(message);
        String type = (String) msg.get("type");
        
        if (type == null) {
            sendMessage(Protocol.createErrorMessage("Invalid message format"));
            return;
        }
        
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
        
            // Validate and sanitize inputs
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
    
    private void handleLogin(Map<String, Object> msg) {
        String username = (String) msg.get("username");
        String password = (String) msg.get("password");
        
        if (username == null || password == null) {
            sendMessage(Protocol.createErrorMessage("Username and password required"));
            return;
        }
        
        // Sanitize inputs
        try {
            username = SecurityUtils.sanitize(username);
            password = SecurityUtils.sanitize(password);
        } catch (Exception e) {
            sendMessage(Protocol.createErrorMessage("Invalid input format"));
            return;
        }
        
        // Check rate limiting
        String rateLimitKey = clientAddress + ":" + username;
        if (rateLimiter.isRateLimited(rateLimitKey)) {
            long remaining = rateLimiter.getRemainingLockoutTime(rateLimitKey);
            sendMessage(Protocol.createErrorMessage("Too many failed attempts. Please try again in " + 
                remaining + " seconds."));
            return;
        }
        
        if (userManager.login(username, password)) {
            if (userManager.isOnline(username)) {
                sendMessage(Protocol.createErrorMessage("User already logged in"));
                return;
            }
            
            // Successful login - clear rate limit
            rateLimiter.recordSuccess(rateLimitKey);
            
            this.username = username;
            this.authenticated = true;
            userManager.setOnline(username, sessionId);
            server.addClient(this);
            
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
            
            // Notify other players
            server.broadcastPlayerList();
        } else {
            // Failed login - record attempt
            rateLimiter.recordFailedAttempt(rateLimitKey);
            sendMessage(Protocol.createErrorMessage("Incorrect credentials"));
        }
    }
    
    private void handleListPlayers() {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String[] onlineUsers = userManager.getOnlineUsers();
        StringBuilder playersList = new StringBuilder();
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
    
    private void handleChallengeResponse(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String challenger = (String) msg.get("challenger");
        String response = (String) msg.get("response"); // "ACCEPT" or "REJECT"
        
        if (challenger == null || response == null) {
            sendMessage(Protocol.createErrorMessage("Invalid challenge response"));
            return;
        }
        
        server.handleChallengeResponse(challenger, username, response);
    }
    
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
        cleanup();
    }
    
    private void handleRematchRequest(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String opponent = (String) msg.get("opponent");
        if (opponent == null) {
            // Try to get last opponent from server
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
    
    private void handleRematchResponse(Map<String, Object> msg) {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        String opponent = (String) msg.get("opponent");
        String response = (String) msg.get("response"); // "ACCEPT" or "REJECT"
        
        if (opponent == null || response == null) {
            sendMessage(Protocol.createErrorMessage("Invalid rematch response"));
            return;
        }
        
        server.handleRematchResponse(opponent, username, response);
    }
    
    private void handleLeaderboard() {
        if (!authenticated) {
            sendMessage(Protocol.createErrorMessage("Not authenticated"));
            return;
        }
        
        server.sendLeaderboard(this);
    }
    
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

