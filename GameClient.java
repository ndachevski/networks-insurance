// Students: CSY23102, CSY23052, CSY23031

import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * GameClient is the CLI client that connects to the game server. It handles the socket connection,
 * spawns a listener thread to read server messages asynchronously, and manages local player state
 * including board display. Messages are sent to the server and responses are routed back to the UI.
 * Uses blocking socket reads in background thread to prevent CLI from freezing during network IO.
 */
public class GameClient {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ServerListener listener;
    private Player player;
    private GameUI ui;
    private String currentGameId;
    private String currentOpponent;
    // local copy of board state for display
    private char[][] currentBoard;
    // track whose turn it is during game
    private String currentPlayer;
    private boolean inGame;
    
    public GameClient() {
        player = new Player();
        ui = new GameUI(this);
        currentBoard = new char[3][3];
        inGame = false;
    }
    
    /**
     * establish connection to server - spawns listener thread for async message reading.
     * connection is nonblocking from the UI perspective: listener thread does the blocking reads.
     */
    public boolean connect() {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // start listener in separate thread so we don't block on reading
            listener = new ServerListener(in, this);
            listener.start();
            
            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * handle incoming messages from server - parse type and route to appropriate handler
     */
    public void handleServerMessage(String message) {
        Map<String, Object> msg = Protocol.parseMessage(message);
        String type = (String) msg.get("type");
        
        if (type == null) {
            return;
        }
        
        // dispatch to handler based on message type
        switch (type) {
            case "LOGIN_SUCCESS":
                handleLoginSuccess(msg);
                break;
            case "SUCCESS":
                ui.showMessage((String) msg.get("message"));
                break;
            case "ERROR":
                ui.showError((String) msg.get("message"));
                break;
            case "PLAYERS_LIST":
                handlePlayersList(msg);
                break;
            case "CHALLENGE":
                handleChallenge(msg);
                break;
            case "CHALLENGE_RESPONSE":
                handleChallengeResponse(msg);
                break;
            case "START_GAME":
                handleStartGame(msg);
                break;
            case "UPDATE":
                handleUpdate(msg);
                break;
            case "RESULT":
                handleResult(msg);
                break;
            case "OPPONENT_DISCONNECTED":
                handleOpponentDisconnected(msg);
                break;
            case "REMATCH_REQUEST":
                handleRematchRequest(msg);
                break;
            case "REMATCH_RESPONSE":
                handleRematchResponse(msg);
                break;
            case "LEADERBOARD":
                handleLeaderboard(msg);
                break;
        }
    }
    
    /**
     * handle successful login - store player info and display stats
     */
    private void handleLoginSuccess(Map<String, Object> msg) {
        player.setUsername((String) msg.get("username"));
        player.setStats(
            Integer.parseInt((String) msg.get("wins")),
            Integer.parseInt((String) msg.get("losses")),
            Integer.parseInt((String) msg.get("draws"))
        );
        player.setAuthenticated(true);
        ui.showMessage("Login successful! Welcome " + player.getUsername());
        ui.showStats();
    }
    
    /**
     * update player list when server broadcasts changes
     */
    private void handlePlayersList(Map<String, Object> msg) {
        String playersStr = (String) msg.get("players");
        if (playersStr != null && !playersStr.isEmpty()) {
            String[] players = playersStr.split(",");
            ui.updatePlayersList(players);
        } else {
            ui.updatePlayersList(new String[0]);
        }
    }
    
    /**
     * incoming challenge from opponent - notify user and store for accept/reject
     */
    private void handleChallenge(Map<String, Object> msg) {
        String challenger = (String) msg.get("challenger");
        ui.showChallenge(challenger);
    }
    
    /**
     * rematch request from opponent - notify and store for response
     */
    private void handleRematchRequest(Map<String, Object> msg) {
        String requester = (String) msg.get("requester");
        ui.showMessage(requester + " wants a rematch! Type 'accept' to accept or 'reject' to reject");
        // store for later when they accept/reject
        ui.setPendingRematchRequester(requester);
    }
    
    /**
     * response to our rematch request
     */
    private void handleRematchResponse(Map<String, Object> msg) {
        String response = (String) msg.get("response");
        String opponent = (String) msg.get("opponent");
        
        if ("ACCEPT".equals(response)) {
            ui.showMessage(opponent + " accepted your rematch request!");
        } else {
            ui.showMessage(opponent + " declined your rematch request.");
        }
    }
    
    /**
     * display formatted leaderboard from server data
     */
    private void handleLeaderboard(Map<String, Object> msg) {
        String data = (String) msg.get("data");
        if (data == null || data.isEmpty()) {
            ui.showError("No leaderboard data available");
            return;
        }
        
        String[] entries = data.split("\\|");
        System.out.println("\n=== LEADERBOARD ===");
        System.out.println("Rank | Player     | Wins | Losses | Draws");
        System.out.println("-----|------------|------|--------|------");
        
        for (String entry : entries) {
            String[] parts = entry.split(",");
            if (parts.length >= 5) {
                System.out.printf("%-4s | %-10s | %-4s | %-6s | %-5s%n", 
                    parts[0], parts[1], parts[2], parts[3], parts[4]);
            }
        }
        System.out.println("=====================\n");
    }
    
    /**
     * response to our challenge attempt
     */
    private void handleChallengeResponse(Map<String, Object> msg) {
        String response = (String) msg.get("response");
        String opponent = (String) msg.get("opponent");
        
        if ("ACCEPT".equals(response)) {
            ui.showMessage(opponent + " accepted your challenge!");
        } else {
            ui.showMessage(opponent + " rejected your challenge.");
        }
    }
    
    /**
     * game starting - initialize local board and set current turn info
     */
    private void handleStartGame(Map<String, Object> msg) {
        currentGameId = (String) msg.get("gameId");
        String player1 = (String) msg.get("player1");
        String player2 = (String) msg.get("player2");
        currentPlayer = (String) msg.get("currentPlayer");
        
        // figure out who we're playing (determine opponent based on our username)
        currentOpponent = player1.equals(player.getUsername()) ? player2 : player1;
        inGame = true;
        
        // initialize empty board
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                currentBoard[i][j] = ' ';
            }
        }
        
        ui.showMessage("Game started! You are playing against " + currentOpponent);
        ui.showBoard();
        if (currentPlayer.equals(player.getUsername())) {
            ui.showMessage("Your turn!");
        } else {
            ui.showMessage("Waiting for " + currentOpponent + "'s move...");
        }
    }
    
    /**
     * board update from server - sync our local board and show whose turn it is
     */
    private void handleUpdate(Map<String, Object> msg) {
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        currentPlayer = (String) msg.get("currentPlayer");
        
        if (boardMap != null) {
            // sync board state with server
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                currentBoard[x][y] = entry.getValue().charAt(0);
            }
        }
        
        ui.showBoard();
        if (currentPlayer.equals(player.getUsername())) {
            ui.showMessage("Your turn!");
        } else {
            ui.showMessage("Waiting for " + currentOpponent + "'s move...");
        }
    }
    
    /**
     * game result received - show final board and outcome, update local stats
     */
    private void handleResult(Map<String, Object> msg) {
        String result = (String) msg.get("result");
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        
        // sync final board state
        if (boardMap != null) {
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                currentBoard[x][y] = entry.getValue().charAt(0);
            }
        }
        
        ui.showBoard();
        inGame = false;
        
        if ("WIN".equals(result)) {
            ui.showMessage("Congratulations! You won!");
        } else if ("LOSS".equals(result)) {
            ui.showMessage("You lost. Better luck next time!");
        } else {
            ui.showMessage("It's a draw!");
        }
        
        // reset game state
        currentGameId = null;
        currentOpponent = null;
    }
    
    /**
     * opponent disconnected or left the game - game is over
     */
    private void handleOpponentDisconnected(Map<String, Object> msg) {
        ui.showError("Your opponent disconnected. Game ended.");
        inGame = false;
        currentGameId = null;
        currentOpponent = null;
    }
    
    /**
     * send raw message to server
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    /**
     * send registration request with profile info
     */
    public void register(String username, String password) {
        register(username, password, "", "");
    }
    
    /**
     * send registration request with full profile info to server
     */
    public void register(String username, String password, String name, String email) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REGISTER");
        msg.put("username", username);
        msg.put("password", password);
        msg.put("name", name);
        msg.put("email", email);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * send login request
     */
    public void login(String username, String password) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LOGIN");
        msg.put("username", username);
        msg.put("password", password);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * request updated player list from server
     */
    public void listPlayers() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LIST_PLAYERS");
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * send challenge to a player
     */
    public void challenge(String opponent) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE");
        msg.put("opponent", opponent);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * respond to incoming challenge
     */
    public void respondToChallenge(String challenger, String response) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE_RESPONSE");
        msg.put("challenger", challenger);
        msg.put("response", response);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * send a move during gameplay - coordinates and game id to server
     */
    public void makeMove(int x, int y) {
        if (currentGameId == null || !inGame) {
            ui.showError("Not in a game");
            return;
        }
        
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("x", String.valueOf(x));
        data.put("y", String.valueOf(y));
        
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "MOVE");
        msg.put("gameId", currentGameId);
        msg.put("player", player.getUsername());
        msg.put("data", data);
        
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * request rematch with last opponent - server will validate they're available
     */
    public void requestRematch() {
        if (currentOpponent == null) {
            ui.showError("No previous opponent found");
            return;
        }
        
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_REQUEST");
        msg.put("opponent", currentOpponent);
        sendMessage(Protocol.createMessage(msg));
        ui.showMessage("Requesting rematch with " + currentOpponent + "...");
    }
    
    /**
     * respond to rematch request
     */
    public void respondToRematch(String requester, String response) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_RESPONSE");
        msg.put("opponent", requester);
        msg.put("response", response);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * request leaderboard from server
     */
    public void requestLeaderboard() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LEADERBOARD");
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * notify server we're leaving the game and close connection
     */
    public void logout() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LOGOUT");
        sendMessage(Protocol.createMessage(msg));
        disconnect();
    }
    
    /**
     * close socket connection
     */
    public void disconnect() {
        if (listener != null) {
            listener.stopListening();
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }
    
    /**
     * handle server disconnection - notify user and exit
     */
    public void handleServerDisconnection(String reason) {
        System.out.println("\n=== SERVER DISCONNECTED ===");
        System.out.println("Connection to server lost: " + reason);
        System.out.println("The application will now close.");
        System.out.println("===========================\n");
        
        disconnect();
        System.exit(0);
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public char[][] getCurrentBoard() {
        return currentBoard;
    }
    
    public boolean isInGame() {
        return inGame;
    }
    
    public String getCurrentPlayer() {
        return currentPlayer;
    }
    
    public static void main(String[] args) {
        GameClient client = new GameClient();
        
        if (!client.connect()) {
            System.err.println("Failed to connect to server");
            return;
        }
        
        client.ui.run();
        client.disconnect();
    }
}
