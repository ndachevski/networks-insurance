import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * GameClient.java - client side socket connection and message handling
 * this class connect to server, send command from user, receive response from server
 * it communicate with GameUI (show message to user) and Server (send/receive message)
 */
public class GameClient {
    // server address and port
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    
    // socket connection to server
    private Socket socket;
    // read message from server
    private BufferedReader in;
    // send message to server
    private PrintWriter out;
    // thread that listen for server message (run in background)
    private ServerListener listener;
    // current player info (username, wins/loss/draw stat)
    private Player player;
    // ui - show message to player, get command from player
    private GameUI ui;
    // current game info
    private String currentGameId; // unique game id when in game
    private String currentOpponent; // who playing against
    private char[][] currentBoard; // current board state (3x3 with X/O)
    private String currentPlayer; // whose turn (username of player whose turn it is)
    private boolean inGame; // flag if in active game
    
    /**
     * constructor - initialize client component
     */
    public GameClient() {
        player = new Player();
        ui = new GameUI(this);
        currentBoard = new char[3][3];
        inGame = false;
    }
    
    /**
     * connect to server. create socket, setup input/output stream, start listener thread
     */
    public boolean connect() {
        try {
            // create socket to server
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            // setup input stream (read from server)
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            // setup output stream (send to server, auto flush with PrintWriter true)
            out = new PrintWriter(socket.getOutputStream(), true);
            
            // start listener thread to read message from server in background
            listener = new ServerListener(in, this);
            listener.start();
            
            return true;
        } catch (IOException e) {
            System.err.println("Error connecting to server: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * handle message from server. parse and dispatch to appropriate handler
     * this method called by ServerListener when message arrive from server
     */
    public void handleServerMessage(String message) {
        // parse message from server (json format)
        Map<String, Object> msg = Protocol.parseMessage(message);
        String type = (String) msg.get("type");
        
        if (type == null) {
            return;
        }
        
        // switch on message type and call appropriate handler
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
                // list of online player available to challenge
                handlePlayersList(msg);
                break;
            case "CHALLENGE":
                // someone challenge us to game
                handleChallenge(msg);
                break;
            case "CHALLENGE_RESPONSE":
                // response to our challenge (accept or reject)
                handleChallengeResponse(msg);
                break;
            case "START_GAME":
                // game start, we got opponent and game id
                handleStartGame(msg);
                break;
            case "UPDATE":
                // board update (opponent move)
                handleUpdate(msg);
                break;
            case "RESULT":
                // game end, we got result (win/loss/draw)
                handleResult(msg);
                break;
            case "OPPONENT_DISCONNECTED":
                // opponent disconnect, game end
                handleOpponentDisconnected(msg);
                break;
            case "REMATCH_REQUEST":
                // opponent ask for rematch
                handleRematchRequest(msg);
                break;
            case "REMATCH_RESPONSE":
                // response to our rematch request (accept or reject)
                handleRematchResponse(msg);
                break;
            case "LEADERBOARD":
                // leaderboard data (top player stats)
                handleLeaderboard(msg);
                break;
        }
    }
    
    /**
     * handle login success response. set player info and show message
     */
    private void handleLoginSuccess(Map<String, Object> msg) {
        player.setUsername((String) msg.get("username"));
        // set player stats (wins, losses, draws from server)
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
     * handle players list response. show list of online player
     */
    private void handlePlayersList(Map<String, Object> msg) {
        String playersStr = (String) msg.get("players");
        if (playersStr != null && !playersStr.isEmpty()) {
            // split comma separated list and update ui
            String[] players = playersStr.split(",");
            ui.updatePlayersList(players);
        } else {
            // empty player list
            ui.updatePlayersList(new String[0]);
        }
    }
    
    /**
     * handle challenge message from opponent. show who challenge us
     */
    private void handleChallenge(Map<String, Object> msg) {
        String challenger = (String) msg.get("challenger");
        ui.showChallenge(challenger);
    }
    
    /**
     * handle rematch request message. opponent ask for rematch
     */
    private void handleRematchRequest(Map<String, Object> msg) {
        String requester = (String) msg.get("requester");
        ui.showMessage(requester + " wants a rematch! Type 'accept' to accept or 'reject' to reject");
        // store requester so accept/reject command know who requesting
        ui.setPendingRematchRequester(requester);
    }
    
    /**
     * handle rematch response message. opponent accept/reject our rematch
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
     * handle leaderboard message. show top player with rank and stat
     */
    private void handleLeaderboard(Map<String, Object> msg) {
        String data = (String) msg.get("data");
        if (data == null || data.isEmpty()) {
            ui.showError("No leaderboard data available");
            return;
        }
        
        // parse leaderboard format: rank,username,wins,losses,draws|rank,username,wins,losses,draws|...
        String[] entries = data.split("\\|");
        System.out.println("\n=== LEADERBOARD ===");
        System.out.println("Rank | Player     | Wins | Losses | Draws");
        System.out.println("-----|------------|------|--------|------");
        
        // print each player formatted in table
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
     * handle challenge response message. opponent accept/reject our challenge
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
     * handle game start message. we get opponent, game id, board. game now active
     */
    private void handleStartGame(Map<String, Object> msg) {
        currentGameId = (String) msg.get("gameId");
        String player1 = (String) msg.get("player1");
        String player2 = (String) msg.get("player2");
        currentPlayer = (String) msg.get("currentPlayer");
        
        // figure out opponent (the other player)
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
        // check whose turn
        if (currentPlayer.equals(player.getUsername())) {
            ui.showMessage("Your turn!");
        } else {
            ui.showMessage("Waiting for " + currentOpponent + "'s move...");
        }
    }
    
    /**
     * handle board update message. opponent make move, board change
     */
    private void handleUpdate(Map<String, Object> msg) {
        // get updated board from server
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        currentPlayer = (String) msg.get("currentPlayer");
        
        // update our board with server state
        if (boardMap != null) {
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                // key is "x,y" format
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                // value is character (X or O)
                currentBoard[x][y] = entry.getValue().charAt(0);
            }
        }
        
        // show updated board
        ui.showBoard();
        // check whose turn now
        if (currentPlayer.equals(player.getUsername())) {
            ui.showMessage("Your turn!");
        } else {
            ui.showMessage("Waiting for " + currentOpponent + "'s move...");
        }
    }
    
    /**
     * handle game result message. game over, we get result (win/loss/draw)
     */
    private void handleResult(Map<String, Object> msg) {
        String result = (String) msg.get("result");
        // get final board state
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        
        // update board to final state
        if (boardMap != null) {
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                currentBoard[x][y] = entry.getValue().charAt(0);
            }
        }
        
        // show final board
        ui.showBoard();
        // game no longer active
        inGame = false;
        
        // show result message
        if ("WIN".equals(result)) {
            ui.showMessage("Congratulations! You won!");
        } else if ("LOSS".equals(result)) {
            ui.showMessage("You lost. Better luck next time!");
        } else {
            ui.showMessage("It's a draw!");
        }
        
        // clear game state
        currentGameId = null;
        currentOpponent = null;
    }
    
    /**
     * handle opponent disconnect message. opponent left game mid-way
     */
    private void handleOpponentDisconnected(Map<String, Object> msg) {
        ui.showError("Your opponent disconnected. Game ended.");
        inGame = false;
        currentGameId = null;
        currentOpponent = null;
    }
    
    /**
     * low level send message to server
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    /**
     * send register command to server (simple version without name/email)
     */
    public void register(String username, String password) {
        register(username, password, "", "");
    }
    
    /**
     * send register command to server with all info
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
     * send login command to server
     */
    public void login(String username, String password) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LOGIN");
        msg.put("username", username);
        msg.put("password", password);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * request list of online player from server
     */
    public void listPlayers() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LIST_PLAYERS");
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * send challenge command to opponent
     */
    public void challenge(String opponent) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE");
        msg.put("opponent", opponent);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * respond to challenge (accept or reject)
     */
    public void respondToChallenge(String challenger, String response) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE_RESPONSE");
        msg.put("challenger", challenger);
        msg.put("response", response);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * send move command to server (x,y coordinate on 3x3 board)
     */
    public void makeMove(int x, int y) {
        if (currentGameId == null || !inGame) {
            ui.showError("Not in a game");
            return;
        }
        
        // create move data
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("x", String.valueOf(x));
        data.put("y", String.valueOf(y));
        
        // create move message
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "MOVE");
        msg.put("gameId", currentGameId);
        msg.put("player", player.getUsername());
        msg.put("data", data);
        
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * request rematch with last opponent
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
     * respond to rematch request (accept or reject)
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
     * logout and disconnect from server
     */
    public void logout() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LOGOUT");
        sendMessage(Protocol.createMessage(msg));
        disconnect();
    }
    
    /**
     * disconnect from server (close socket)
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
     * handle server disconnection - server went down or connection lost
     */
    public void handleServerDisconnection(String reason) {
        System.out.println("\n=== SERVER DISCONNECTED ===");
        System.out.println("Connection to server lost: " + reason);
        System.out.println("The application will now close.");
        System.out.println("===========================\n");
        
        disconnect();
        System.exit(0);
    }
    
    /**
     * getter for player object
     */
    public Player getPlayer() {
        return player;
    }
    
    /**
     * getter for current game board
     */
    public char[][] getCurrentBoard() {
        return currentBoard;
    }
    
    /**
     * check if currently in active game
     */
    public boolean isInGame() {
        return inGame;
    }
    
    /**
     * get whose turn it is
     */
    public String getCurrentPlayer() {
        return currentPlayer;
    }
    
    /**
     * main - create client and connect, then show ui
     */
    public static void main(String[] args) {
        GameClient client = new GameClient();
        
        // try to connect to server
        if (!client.connect()) {
            System.err.println("Failed to connect to server");
            return;
        }
        
        // show ui (block until user quit)
        client.ui.run();
        // clean up when quit
        client.disconnect();
    }
}

