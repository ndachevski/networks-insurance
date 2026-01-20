import java.util.Scanner;

/**
 * GameUI.java - Command Line Interface for player to type command
 * this class show menu and read user input from keyboard. it parse what user type and call
 * appropriate method on game client to handle request. it also display result back to user
 */
public class GameUI {
    private GameClient client;
    private Scanner scanner; // read input from keyboard
    private String pendingChallenger; // store who challenge us so we can accept/reject later
    private String pendingRematchRequester; // store who request rematch so we can respond
    
    public GameUI(GameClient client) {
        this.client = client;
        this.scanner = new Scanner(System.in); // create scanner to read from keyboard
        this.pendingChallenger = null;
        this.pendingRematchRequester = null;
    }
    
    /**
     * main loop - show prompt and process user command until they quit
     */
    public void run() {
        System.out.println("=== Tic-Tac-Toe Game Client ===");
        System.out.println("Type 'help' for commands");
        
        while (true) {
            System.out.print("> "); // show prompt
            String command = scanner.nextLine().trim(); // read what user type
            
            // skip if user just press enter without typing
            if (command.isEmpty()) {
                continue;
            }
            
            // split command into parts so we can get command name and arguments
            String[] parts = command.split("\\s+");
            String cmd = parts[0].toLowerCase(); // get command name and convert to lowercase
            
            // check what command user type and handle it
            switch (cmd) {
                // user want register new account
                case "register":
                    handleRegister(parts);
                    break;
                    
                // user want login with username and password
                case "login":
                    if (parts.length >= 3) {
                        client.login(parts[1], parts[2]);
                    } else {
                        showError("Usage: login <username> <password>");
                    }
                    break;
                    
                // user want see list of online player
                case "list":
                    if (client.getPlayer().isAuthenticated()) {
                        client.listPlayers();
                    } else {
                        showError("Please login first");
                    }
                    break;
                    
                // user want challenge another player
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
                    
                // user accept challenge from someone
                case "accept":
                    if (pendingChallenger != null) {
                        client.respondToChallenge(pendingChallenger, "ACCEPT");
                        pendingChallenger = null; // clear so we not accept same challenge twice
                    } else if (pendingRematchRequester != null) {
                        client.respondToRematch(pendingRematchRequester, "ACCEPT");
                        pendingRematchRequester = null;
                    } else {
                        showError("No pending challenge or rematch");
                    }
                    break;
                    
                // user reject challenge or rematch
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
                    
                // user make move during game by giving x,y coordinate
                case "move":
                    if (parts.length >= 3) {
                        if (client.isInGame()) {
                            try {
                                // parse x and y coordinate from command
                                int x = Integer.parseInt(parts[1]);
                                int y = Integer.parseInt(parts[2]);
                                // check if coordinate valid (must be 0-2 for 3x3 board)
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
                    
                // user want see current board during game
                case "board":
                    if (client.isInGame()) {
                        showBoard();
                    } else {
                        showError("Not in a game");
                    }
                    break;
                    
                // user want see their win/loss/draw statistics
                case "stats":
                    showStats();
                    break;
                    
                // user want request rematch with previous opponent
                case "rematch":
                    client.requestRematch();
                    break;
                    
                // user want see top player leaderboard
                case "leaderboard":
                    client.requestLeaderboard();
                    break;
                    
                // user want logout
                case "logout":
                    client.logout();
                    System.out.println("Logged out. Goodbye!");
                    return; // exit the run loop
                    
                // user want quit and exit client
                case "quit":
                case "exit":
                    client.logout();
                    System.out.println("Goodbye!");
                    return; // exit the run loop
                    
                // user want see list of command
                case "help":
                    showHelp();
                    break;
                    
                // user type something we don't recognize
                default:
                    showError("Unknown command. Type 'help' for commands");
            }
        }
    }
    
    // display info message to user
    public void showMessage(String message) {
        System.out.println("[INFO] " + message);
    }
    
    // display error message to user
    public void showError(String error) {
        System.out.println("[ERROR] " + error);
    }
    
    // show player current win/loss/draw statistics
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
    
    // show list of player currently online
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
    
    // show that someone challenge us and prompt to accept/reject
    public void showChallenge(String challenger) {
        this.pendingChallenger = challenger;
        System.out.println("\n=== CHALLENGE ===");
        System.out.println(challenger + " has challenged you to a game!");
        System.out.println("Type 'accept' to accept or 'reject' to reject");
        System.out.println("==================\n");
    }
    
    // store who challenge us (used when we receive challenge from server)
    public void setPendingChallenger(String challenger) {
        this.pendingChallenger = challenger;
    }
    
    // store who request rematch (used when we receive rematch request)
    public void setPendingRematchRequester(String requester) {
        this.pendingRematchRequester = requester;
    }
    
    // display the tic-tac-toe board with coordinate so user know where to move
    public void showBoard() {
        char[][] board = client.getCurrentBoard();
        System.out.println("\n  0   1   2"); // show column number
        for (int i = 0; i < 3; i++) {
            System.out.print(i + " "); // show row number
            for (int j = 0; j < 3; j++) {
                char cell = board[i][j];
                if (cell == ' ') {
                    System.out.print(" "); // empty cell show as space
                } else {
                    System.out.print(cell); // show X or O
                }
                if (j < 2) {
                    System.out.print(" | "); // separator between cell
                }
            }
            System.out.println();
            if (i < 2) {
                System.out.println("  ---------"); // line between row
            }
        }
        System.out.println();
    }
    
    // handle user register command - ask for name and email
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
            
            // send registration request to server
            client.register(parts[1], parts[2], name, email);
        } else {
            showError("Usage: register <username> <password>");
            showError("You will be prompted for name and email");
        }
    }
    
    // show all available command to user
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

