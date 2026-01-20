import java.util.Scanner;

/**
 * GameUI.java - Command Line Interface for player actions
 */
public class GameUI {
    private GameClient client;
    private Scanner scanner;
    private String pendingChallenger;
    private String pendingRematchRequester;
    
    public GameUI(GameClient client) {
        this.client = client;
        this.scanner = new Scanner(System.in);
        this.pendingChallenger = null;
        this.pendingRematchRequester = null;
    }
    
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
                    if (parts.length >= 3) {
                        client.login(parts[1], parts[2]);
                    } else {
                        showError("Usage: login <username> <password>");
                    }
                    break;
                    
                case "list":
                    if (client.getPlayer().isAuthenticated()) {
                        client.listPlayers();
                    } else {
                        showError("Please login first");
                    }
                    break;
                    
                case "challenge":
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
                    if (parts.length >= 3) {
                        if (client.isInGame()) {
                            try {
                                int x = Integer.parseInt(parts[1]);
                                int y = Integer.parseInt(parts[2]);
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
                    if (client.isInGame()) {
                        showBoard();
                    } else {
                        showError("Not in a game");
                    }
                    break;
                    
                case "stats":
                    showStats();
                    break;
                    
                case "rematch":
                    client.requestRematch();
                    break;
                    
                case "leaderboard":
                    client.requestLeaderboard();
                    break;
                    
                case "logout":
                    client.logout();
                    System.out.println("Logged out. Goodbye!");
                    return;
                    
                case "quit":
                case "exit":
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
    
    public void showMessage(String message) {
        System.out.println("[INFO] " + message);
    }
    
    public void showError(String error) {
        System.out.println("[ERROR] " + error);
    }
    
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
    
    public void showChallenge(String challenger) {
        this.pendingChallenger = challenger;
        System.out.println("\n=== CHALLENGE ===");
        System.out.println(challenger + " has challenged you to a game!");
        System.out.println("Type 'accept' to accept or 'reject' to reject");
        System.out.println("==================\n");
    }
    
    public void setPendingChallenger(String challenger) {
        this.pendingChallenger = challenger;
    }
    
    public void setPendingRematchRequester(String requester) {
        this.pendingRematchRequester = requester;
    }
    
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
            
            client.register(parts[1], parts[2], name, email);
        } else {
            showError("Usage: register <username> <password>");
            showError("You will be prompted for name and email");
        }
    }
    
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

