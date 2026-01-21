// Students: CSY23102, CSY23052, CSY23031

import java.util.Scanner;

/**
 * GameUI.java - Command Line Interface for player actions
 * 
 * this is the CLI wrapper that handles all user input and display for the text-based client.
 * it maintains a command loop reading from stdin, routing commands to the game client, and
 * rendering output to the user. uses simple string-based commands (login, challenge, move, etc)
 * and formats board/stats/leaderboard output for terminal display. also tracks pending challenges
 * and rematch requests to handle accept/reject responses properly.
 */
public class GameUI {
    private GameClient client;
    private Scanner scanner;
    // hold the username of whoever challenged us so we know who we're responding to
    private String pendingChallenger;
    // hold the username of whoever requested a rematch
    private String pendingRematchRequester;
    
    public GameUI(GameClient client) {
        this.client = client;
        this.scanner = new Scanner(System.in);
        this.pendingChallenger = null;
        this.pendingRematchRequester = null;
    }
    
    /**
     * main command loop - reads user input and routes to handlers
     * 
     * runs continuously until user quits/exits. each iteration reads a command line,
     * splits it into tokens, and dispatches to the appropriate handler. some commands
     * require authentication first (list, challenge) so we check player.isAuthenticated().
     */
    public void run() {
        System.out.println("=== Tic-Tac-Toe Game Client ===");
        System.out.println("Type 'help' for commands");
        
        while (true) {
            System.out.print("> ");
            String command = scanner.nextLine().trim();
            
            if (command.isEmpty()) {
                continue;
            }
            
            String[] parts = command.split("\\s+");
            String cmd = parts[0].toLowerCase();
            
            switch (cmd) {
                case "register":
                    handleRegister(parts);
                    break;
                    
                case "login":
                    // login requires username and password args
                    if (parts.length >= 3) {
                        client.login(parts[1], parts[2]);
                    } else {
                        showError("Usage: login <username> <password>");
                    }
                    break;
                    
                case "list":
                    // only authenticated players can list others - security check
                    if (client.getPlayer().isAuthenticated()) {
                        client.listPlayers();
                    } else {
                        showError("Please login first");
                    }
                    break;
                    
                case "challenge":
                    // send challenge to target opponent
                    if (parts.length >= 2) {
                        if (client.getPlayer().isAuthenticated()) {
                            client.challenge(parts[1]);
                        } else {
                            showError("Please login first");
                        }
                    } else {
                        showError("Usage: challenge <username>");
                    }
                    break;
                    
                case "accept":
                    // accept either a challenge or rematch request (whichever is pending)
                    // only one should be pending at a time per game state
                    if (pendingChallenger != null) {
                        client.respondToChallenge(pendingChallenger, "ACCEPT");
                        pendingChallenger = null;
                    } else if (pendingRematchRequester != null) {
                        client.respondToRematch(pendingRematchRequester, "ACCEPT");
                        pendingRematchRequester = null;
                    } else {
                        showError("No pending challenge or rematch");
                    }
                    break;
                    
                case "reject":
                    // reject either a challenge or rematch request, then clear the pending state
                    if (pendingChallenger != null) {
                        client.respondToChallenge(pendingChallenger, "REJECT");
                        pendingChallenger = null;
                    } else if (pendingRematchRequester != null) {
                        client.respondToRematch(pendingRematchRequester, "REJECT");
                        pendingRematchRequester = null;
                    } else {
                        showError("No pending challenge or rematch");
                    }
                    break;
                    
                case "move":
                    // make a move during game - requires x,y coords (0-2), validates bounds before sending
                    if (parts.length >= 3) {
                        if (client.isInGame()) {
                            try {
                                int x = Integer.parseInt(parts[1]);
                                int y = Integer.parseInt(parts[2]);
                                // bounds check on client side before wasting a server message
                                if (x >= 0 && x < 3 && y >= 0 && y < 3) {
                                    client.makeMove(x, y);
                                } else {
                                    showError("Coordinates must be 0-2");
                                }
                            } catch (NumberFormatException e) {
                                showError("Invalid coordinates");
                            }
                        } else {
                            showError("Not in a game");
                        }
                    } else {
                        showError("Usage: move <x> <y> (0-2)");
                    }
                    break;
                    
                case "board":
                    // display current game board if we're in a game
                    if (client.isInGame()) {
                        showBoard();
                    } else {
                        showError("Not in a game");
                    }
                    break;
                    
                case "stats":
                    // show local player stats from their Player object
                    showStats();
                    break;
                    
                case "rematch":
                    // request rematch with previous opponent
                    client.requestRematch();
                    break;
                    
                case "leaderboard":
                    // request top players ranking from server
                    client.requestLeaderboard();
                    break;
                    
                case "logout":
                    // send logout message to server and cleanly exit command loop
                    client.logout();
                    System.out.println("Logged out. Goodbye!");
                    return;
                    
                case "quit":
                case "exit":
                    // exit without sending logout (handles both cases)
                    client.logout();
                    System.out.println("Goodbye!");
                    return;
                    
                case "help":
                    showHelp();
                    break;
                    
                default:
                    showError("Unknown command. Type 'help' for commands");
            }
        }
    }
    
    /**
     * display formatted info message to stdout
     */
    public void showMessage(String message) {
        System.out.println("[INFO] " + message);
    }
    
    /**
     * display formatted error message to stdout
     */
    public void showError(String error) {
        System.out.println("[ERROR] " + error);
    }
    
    /**
     * print player stats from their Player object
     * 
     * only shows stats if player is authenticated - displays win/loss/draw counts and username
     */
    public void showStats() {
        Player player = client.getPlayer();
        if (player.isAuthenticated()) {
            System.out.println("\n=== Your Statistics ===");
            System.out.println("Username: " + player.getUsername());
            System.out.println("Wins: " + player.getWins());
            System.out.println("Losses: " + player.getLosses());
            System.out.println("Draws: " + player.getDraws());
            System.out.println("=====================\n");
        } else {
            showError("Not logged in");
        }
    }
    
    /**
     * display list of online players received from server
     * 
     * called by game client when LIST_PLAYERS response arrives with player array.
     * excludes self (user can't challenge themselves)
     */
    public void updatePlayersList(String[] players) {
        if (players.length > 0) {
            System.out.println("\n=== Online Players ===");
            for (String player : players) {
                System.out.println("- " + player);
            }
            System.out.println("======================\n");
        } else {
            System.out.println("\nNo other players online\n");
        }
    }
    
    /**
     * notify user of incoming challenge and store pending challenger for accept/reject response
     * 
     * stores challenger name so accept/reject commands know exactly who to respond to
     */
    public void showChallenge(String challenger) {
        this.pendingChallenger = challenger;
        System.out.println("\n=== CHALLENGE ===");
        System.out.println(challenger + " has challenged you to a game!");
        System.out.println("Type 'accept' to accept or 'reject' to reject");
        System.out.println("==================\n");
    }
    
    /**
     * store pending challenger username for later accept/reject
     */
    public void setPendingChallenger(String challenger) {
        this.pendingChallenger = challenger;
    }
    
    /**
     * store pending rematch requester username for later accept/reject
     */
    public void setPendingRematchRequester(String requester) {
        this.pendingRematchRequester = requester;
    }
    
    /**
     * render tic-tac-toe board to stdout with grid lines and row/col labels
     * 
     * displays 3x3 board using coordinates (row, col) from 0-2. uses pipes and dashes
     * to draw grid. spaces shown as empty, X and O for players. called by "board" command
     * and also after each move to show updated state.
     */
    public void showBoard() {
        char[][] board = client.getCurrentBoard();
        System.out.println("\n  0   1   2");
        for (int i = 0; i < 3; i++) {
            System.out.print(i + " ");
            for (int j = 0; j < 3; j++) {
                char cell = board[i][j];
                if (cell == ' ') {
                    System.out.print(" ");
                } else {
                    System.out.print(cell);
                }
                if (j < 2) {
                    System.out.print(" | ");
                }
            }
            System.out.println();
            if (i < 2) {
                System.out.println("  ---------");
            }
        }
        System.out.println();
    }
    
    /**
     * handle register command - prompts for name and email after username and password
     * 
     * takes register command with username and password from parts[1] and parts[2],
     * then prompts user for name and email via stdout. validates that both are non-empty
     * before sending registration request to client which handles serialization and
     * network transmission
     */
    private void handleRegister(String[] parts) {
        if (parts.length >= 3) {
            System.out.print("Enter your name: ");
            String name = scanner.nextLine().trim();
            if (name.isEmpty()) {
                showError("Name is required");
                return;
            }
            
            System.out.print("Enter your email: ");
            String email = scanner.nextLine().trim();
            if (email.isEmpty()) {
                showError("Email is required");
                return;
            }
            
            // delegate to client which serializes and sends to server
            client.register(parts[1], parts[2], name, email);
        } else {
            showError("Usage: register <username> <password>");
            showError("You will be prompted for name and email");
        }
    }
    
    /**
     * print command reference for user
     * 
     * displays all available commands and their syntax with descriptions
     */
    private void showHelp() {
        System.out.println("\n=== Available Commands ===");
        System.out.println("register <username> <password> - Register a new account (prompts for name and email)");
        System.out.println("login <username> <password>    - Login to your account");
        System.out.println("list                           - List online players");
        System.out.println("challenge <username>           - Challenge a player");
        System.out.println("accept                         - Accept a challenge");
        System.out.println("reject                         - Reject a challenge");
        System.out.println("move <x> <y>                   - Make a move (0-2)");
        System.out.println("board                          - Show current board");
        System.out.println("stats                          - Show your statistics");
        System.out.println("rematch                        - Request rematch with last opponent");
        System.out.println("leaderboard                    - Show top players leaderboard");
        System.out.println("logout                         - Logout and disconnect");
        System.out.println("quit/exit                      - Exit the client");
        System.out.println("help                           - Show this help");
        System.out.println("============================\n");
    }
}
