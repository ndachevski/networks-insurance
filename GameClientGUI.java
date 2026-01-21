import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * GameClientGUI.java - GUI version of the game client using Swing
 */
public class GameClientGUI {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private ServerListenerGUI listener;
    private Player player;
    
    // GUI Components
    private JFrame loginFrame;
    private JFrame mainFrame;
    private JFrame gameFrame;
    private JTextField usernameField;
    private JPasswordField passwordField;
    private DefaultListModel<String> playersListModel;
    private JList<String> playersList;
    private JButton[][] boardButtons;
    private JLabel statusLabel; // Main window status
    private JLabel gameStatusLabel; // Game window status
    private JLabel currentPlayerLabel;
    private JLabel statsLabel;
    private JDialog registrationDialog; // Reference to registration dialog
    private JLabel registrationStatusLabel; // Status label in registration dialog
    private boolean isRegistering; // Track if we're in registration process
    private String currentGameId;
    private String currentOpponent;
    private char[][] currentBoard;
    private String currentPlayer;
    private boolean inGame;
    
    public GameClientGUI() {
        player = new Player();
        currentBoard = new char[3][3];
        inGame = false;
        playersListModel = new DefaultListModel<>();
        
        createLoginWindow();
    }
    
    private void createLoginWindow() {
        loginFrame = new JFrame("Tic-Tac-Toe - Login");
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setSize(350, 250);
        loginFrame.setLocationRelativeTo(null);
        loginFrame.setLayout(new BorderLayout(10, 10));
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel("Tic-Tac-Toe Game", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18));
        mainPanel.add(titleLabel, gbc);
        
        // Username
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        mainPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        usernameField = new JTextField(15);
        mainPanel.add(usernameField, gbc);
        
        // Password (with show/hide toggle)
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        passwordField = new JPasswordField(15);
        JCheckBox showPasswordCheck = new JCheckBox("Show");
        showPasswordCheck.addActionListener(e -> {
            passwordField.setEchoChar(showPasswordCheck.isSelected() ? (char) 0 : '\u2022');
        });
        JPanel passwordPanel = new JPanel(new BorderLayout(5, 0));
        passwordPanel.add(passwordField, BorderLayout.CENTER);
        passwordPanel.add(showPasswordCheck, BorderLayout.EAST);
        mainPanel.add(passwordPanel, gbc);
        
        // Buttons
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.5;
        JButton registerButton = new JButton("Register");
        registerButton.addActionListener(e -> showRegistrationDialog());
        mainPanel.add(registerButton, gbc);
        
        gbc.gridx = 1;
        JButton loginButton = new JButton("Login");
        loginButton.addActionListener(e -> handleLogin());
        mainPanel.add(loginButton, gbc);
        
        // Status label
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        statusLabel = new JLabel(" ", JLabel.CENTER);
        statusLabel.setForeground(Color.RED);
        mainPanel.add(statusLabel, gbc);
        
        loginFrame.add(mainPanel, BorderLayout.CENTER);
        loginFrame.setVisible(true);
        
        // Enter key to login
        passwordField.addActionListener(e -> handleLogin());
    }
    
    private void showRegistrationDialog() {
        registrationDialog = new JDialog(loginFrame, "Register New Account", true);
        registrationDialog.setSize(400, 320);
        registrationDialog.setMinimumSize(new Dimension(400, 320)); // Prevent window from shrinking
        registrationDialog.setResizable(false); // Prevent manual resizing
        registrationDialog.setLocationRelativeTo(loginFrame);
        registrationDialog.setLayout(new BorderLayout(10, 10));
        
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        // Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel("Registration", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        mainPanel.add(titleLabel, gbc);
        
        // Username
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        mainPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField regUsernameField = new JTextField(20);
        mainPanel.add(regUsernameField, gbc);
        
        // Password (with show/hide toggle)
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JPasswordField regPasswordField = new JPasswordField(20);
        JCheckBox showRegPasswordCheck = new JCheckBox("Show");
        showRegPasswordCheck.addActionListener(e -> {
            regPasswordField.setEchoChar(showRegPasswordCheck.isSelected() ? (char) 0 : '\u2022');
        });
        JPanel regPasswordPanel = new JPanel(new BorderLayout(5, 0));
        regPasswordPanel.add(regPasswordField, BorderLayout.CENTER);
        regPasswordPanel.add(showRegPasswordCheck, BorderLayout.EAST);
        mainPanel.add(regPasswordPanel, gbc);
        
        // Name
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField regNameField = new JTextField(20);
        mainPanel.add(regNameField, gbc);
        
        // Email
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        mainPanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField regEmailField = new JTextField(20);
        mainPanel.add(regEmailField, gbc);
        
        // Status label
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        registrationStatusLabel = new JLabel(" ", JLabel.CENTER);
        registrationStatusLabel.setForeground(Color.RED);
        mainPanel.add(registrationStatusLabel, gbc);
        
        // Buttons
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton submitButton = new JButton("Register");
        JButton cancelButton = new JButton("Cancel");
        
        // Store reference to dialog for response handling
        final JDialog dialogRef = registrationDialog;
        final JLabel statusRef = registrationStatusLabel;
        
        submitButton.addActionListener(e -> {
            String username = regUsernameField.getText().trim();
            String password = new String(regPasswordField.getPassword());
            String name = regNameField.getText().trim();
            String email = regEmailField.getText().trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                statusRef.setText("Username and password are required");
                statusRef.setForeground(Color.RED);
                return;
            }
            
            if (name.isEmpty()) {
                statusRef.setText("Name is required");
                statusRef.setForeground(Color.RED);
                return;
            }
            
            if (email.isEmpty()) {
                statusRef.setText("Email is required");
                statusRef.setForeground(Color.RED);
                return;
            }
            
            if (!connect()) {
                statusRef.setText("Failed to connect to server");
                statusRef.setForeground(Color.RED);
                return;
            }
            
            // Set registering flag to track registration responses
            isRegistering = true;
            
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("type", "REGISTER");
            msg.put("username", username);
            msg.put("password", password);
            msg.put("name", name);
            msg.put("email", email);
            sendMessage(Protocol.createMessage(msg));
            
            statusRef.setText("Registering...");
            statusRef.setForeground(Color.BLUE);
        });
        
        cancelButton.addActionListener(e -> {
            isRegistering = false; // Reset flag when dialog is closed
            dialogRef.dispose();
        });
        
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);
        
        registrationDialog.add(mainPanel, BorderLayout.CENTER);
        registrationDialog.add(buttonPanel, BorderLayout.SOUTH);
        registrationDialog.setVisible(true);
    }
    
    
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());
        
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Please enter username and password", Color.RED);
            return;
        }
        
        if (!connect()) {
            showStatus("Failed to connect to server", Color.RED);
            return;
        }
        
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LOGIN");
        msg.put("username", username);
        msg.put("password", password);
        sendMessage(Protocol.createMessage(msg));
    }
    
    private boolean connect() {
        if (socket != null && !socket.isClosed()) {
            return true; // Already connected
        }
        
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            
            listener = new ServerListenerGUI(in, this);
            listener.start();
            
            return true;
        } catch (IOException e) {
            showStatus("Connection error: " + e.getMessage(), Color.RED);
            return false;
        }
    }
    
    public void handleServerMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            Map<String, Object> msg = Protocol.parseMessage(message);
            String type = (String) msg.get("type");
            
            if (type == null) {
                return;
            }
            
            switch (type) {
                case "LOGIN_SUCCESS":
                    handleLoginSuccess(msg);
                    break;
                case "SUCCESS":
                    String successMsg = (String) msg.get("message");
                    if (successMsg != null && successMsg.contains("Registration successful")) {
                        // Close registration dialog and return to login screen
                        isRegistering = false; // Reset flag before closing
                        if (registrationDialog != null) {
                            registrationDialog.dispose();
                            registrationDialog = null;
                            registrationStatusLabel = null;
                        }
                        // Show success in login window status (now that isRegistering is false)
                        if (loginFrame != null && statusLabel != null) {
                            showStatus(successMsg + " You can now login.", Color.GREEN);
                        }
                        // Disconnect from server since we're just registering
                        disconnect();
                    } else {
                        // Only show in login window if not registering
                        if (!isRegistering) {
                            showStatus(successMsg, Color.GREEN);
                        }
                    }
                    break;
                case "ERROR":
                    String errorMsg = (String) msg.get("message");
                    // Show errors in registration dialog if we're registering
                    if (isRegistering && registrationStatusLabel != null && registrationDialog != null) {
                        SwingUtilities.invokeLater(() -> {
                            registrationStatusLabel.setText(errorMsg);
                            registrationStatusLabel.setForeground(Color.RED);
                            // Don't pack - keep dialog size fixed
                        });
                        isRegistering = false; // Reset flag after showing error
                    } else {
                        // Show in login/main window if not registering
                        showStatus(errorMsg, Color.RED);
                    }
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
        });
    }
    
    private void handleLoginSuccess(Map<String, Object> msg) {
        player.setUsername((String) msg.get("username"));
        player.setStats(
            Integer.parseInt((String) msg.get("wins")),
            Integer.parseInt((String) msg.get("losses")),
            Integer.parseInt((String) msg.get("draws"))
        );
        player.setName((String) msg.get("name"));
        player.setEmail((String) msg.get("email"));
        // Password is no longer sent from server for security
        // Store empty or use a placeholder
        player.setPassword("***"); // Indicate password is not available
        player.setAuthenticated(true);
        
        loginFrame.setVisible(false);
        createMainWindow();
        listPlayers();
    }
    
    private void createMainWindow() {
        mainFrame = new JFrame("Tic-Tac-Toe - " + player.getUsername());
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(500, 600);
        mainFrame.setLocationRelativeTo(null);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Top panel - Profile/Stats and controls
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        
        // Profile panel with all user information
        JPanel profilePanel = new JPanel(new GridBagLayout());
        profilePanel.setBorder(BorderFactory.createTitledBorder("Your Profile"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        
        // Username
        gbc.gridx = 0; gbc.gridy = 0;
        profilePanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        profilePanel.add(new JLabel(player.getUsername()), gbc);
        
        // Name
        gbc.gridx = 0; gbc.gridy = 1;
        profilePanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        profilePanel.add(new JLabel(player.getName() != null && !player.getName().isEmpty() ? player.getName() : "N/A"), gbc);
        
        // Email
        gbc.gridx = 0; gbc.gridy = 2;
        profilePanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        profilePanel.add(new JLabel(player.getEmail() != null && !player.getEmail().isEmpty() ? player.getEmail() : "N/A"), gbc);
        
        // Statistics
        gbc.gridx = 0; gbc.gridy = 3;
        profilePanel.add(new JLabel("Statistics:"), gbc);
        gbc.gridx = 1;
        statsLabel = new JLabel(String.format("Wins: %d | Losses: %d | Draws: %d", 
            player.getWins(), player.getLosses(), player.getDraws()));
        profilePanel.add(statsLabel, gbc);
        
        topPanel.add(profilePanel, BorderLayout.CENTER);
        
        // Control panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        JButton refreshButton = new JButton("Refresh Players");
        refreshButton.addActionListener(e -> listPlayers());
        JButton leaderboardButton = new JButton("Leaderboard");
        leaderboardButton.addActionListener(e -> requestLeaderboard());
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> logout());
        controlPanel.add(refreshButton);
        controlPanel.add(leaderboardButton);
        controlPanel.add(logoutButton);
        topPanel.add(controlPanel, BorderLayout.SOUTH);
        
        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // Center - Players list
        JPanel playersPanel = new JPanel(new BorderLayout());
        playersPanel.setBorder(BorderFactory.createTitledBorder("Online Players"));
        playersList = new JList<>(playersListModel);
        playersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(playersList);
        playersPanel.add(scrollPane, BorderLayout.CENTER);
        
        // Challenge button
        JButton challengeButton = new JButton("Challenge Selected Player");
        challengeButton.addActionListener(e -> {
            String selected = playersList.getSelectedValue();
            if (selected != null) {
                challenge(selected);
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Please select a player to challenge");
            }
        });
        playersPanel.add(challengeButton, BorderLayout.SOUTH);
        
        mainPanel.add(playersPanel, BorderLayout.CENTER);
        
        // Status area
        statusLabel = new JLabel("Status: In Lobby", JLabel.CENTER);
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        mainPanel.add(statusLabel, BorderLayout.SOUTH);
        
        mainFrame.add(mainPanel);
        mainFrame.setVisible(true);
    }
    
    private void createGameWindow() {
        if (gameFrame != null) {
            gameFrame.dispose();
        }
        
        gameFrame = new JFrame("Tic-Tac-Toe - vs " + currentOpponent);
        gameFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        gameFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                // If in an active game, show confirmation before leaving (forfeit)
                if (inGame && currentGameId != null) {
                    int choice = JOptionPane.showConfirmDialog(
                        gameFrame,
                        "Are you sure you want to leave the match?\n\nYou will forfeit and receive a loss.",
                        "Leave Match?",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                    );
                    if (choice != JOptionPane.YES_OPTION) {
                        return; // User chose No - do not leave, keep window open
                    }
                    // User confirmed Yes - send leave game to server
                    Map<String, Object> msg = new java.util.HashMap<>();
                    msg.put("type", "LEAVE_GAME");
                    msg.put("gameId", currentGameId);
                    sendMessage(Protocol.createMessage(msg));
                }
                
                // Close window and return to lobby
                if (gameFrame != null) {
                    gameFrame.dispose();
                    gameFrame = null;
                }
                if (mainFrame != null) {
                    mainFrame.setVisible(true);
                    mainFrame.toFront();
                }
                
                // Update status to show we're back in lobby
                showStatus("Status: In Lobby", Color.BLUE);
                
                listPlayers();
                inGame = false;
                currentGameId = null;
                currentOpponent = null;
            }
        });
        gameFrame.setSize(400, 550);
        gameFrame.setLocationRelativeTo(null);
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Current player label
        currentPlayerLabel = new JLabel("Your turn!", JLabel.CENTER);
        currentPlayerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        currentPlayerLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        mainPanel.add(currentPlayerLabel, BorderLayout.NORTH);
        
        // Game board
        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 5, 5));
        boardPanel.setBorder(BorderFactory.createTitledBorder("Game Board"));
        boardButtons = new JButton[3][3];
        
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                final int x = i;
                final int y = j;
                JButton button = new JButton(" ");
                button.setFont(new Font("Arial", Font.BOLD, 48));
                button.setPreferredSize(new Dimension(100, 100));
                button.addActionListener(e -> makeMove(x, y));
                boardButtons[i][j] = button;
                boardPanel.add(button);
            }
        }
        
        mainPanel.add(boardPanel, BorderLayout.CENTER);
        
        // Button panel (for after game ends) - Make it more visible
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setBorder(BorderFactory.createTitledBorder("Game Options"));
        buttonPanel.setBackground(Color.WHITE);
        rematchButton = new JButton("Request Rematch");
        rematchButton.setEnabled(false); // Enabled after game ends
        rematchButton.setPreferredSize(new Dimension(160, 35));
        rematchButton.setFont(new Font("Arial", Font.BOLD, 12));
        rematchButton.addActionListener(e -> {
            if (currentOpponent != null) {
                requestRematch();
            } else {
                showStatus("No opponent to rematch", Color.RED);
            }
        });
        lobbyButton = new JButton("Back to Lobby");
        lobbyButton.setEnabled(false); // Enabled after game ends
        lobbyButton.setPreferredSize(new Dimension(160, 35));
        lobbyButton.setFont(new Font("Arial", Font.BOLD, 12));
        lobbyButton.addActionListener(e -> goBackToLobby());
        buttonPanel.add(rematchButton);
        buttonPanel.add(lobbyButton);
        
        // Status label for game window
        gameStatusLabel = new JLabel("Game in progress", JLabel.CENTER);
        gameStatusLabel.setForeground(Color.BLUE);
        gameStatusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        
        JPanel southPanel = new JPanel(new BorderLayout(5, 5));
        southPanel.add(buttonPanel, BorderLayout.NORTH);
        southPanel.add(gameStatusLabel, BorderLayout.SOUTH);
        mainPanel.add(southPanel, BorderLayout.SOUTH);
        
        gameFrame.add(mainPanel);
        gameFrame.setVisible(true);
        
        updateBoardDisplay();
    }
    
    private JButton rematchButton;
    private JButton lobbyButton;
    
    private void updateBoardDisplay() {
        if (boardButtons == null) return;
        
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                char cell = currentBoard[i][j];
                JButton button = boardButtons[i][j];
                
                if (cell == ' ') {
                    button.setText(" ");
                    button.setEnabled(inGame && currentPlayer != null && currentPlayer.equals(player.getUsername()));
                } else {
                    button.setText(String.valueOf(cell));
                    button.setEnabled(false);
                }
            }
        }
        
        if (currentPlayerLabel != null && currentPlayer != null) {
            if (currentPlayer.equals(player.getUsername())) {
                currentPlayerLabel.setText("Your turn!");
                currentPlayerLabel.setForeground(Color.GREEN);
            } else {
                currentPlayerLabel.setText(currentOpponent + "'s turn");
                currentPlayerLabel.setForeground(Color.RED);
            }
        }
    }
    
    private void handlePlayersList(Map<String, Object> msg) {
        String playersStr = (String) msg.get("players");
        playersListModel.clear();
        if (playersStr != null && !playersStr.isEmpty()) {
            String[] players = playersStr.split(",");
            for (String p : players) {
                if (!p.equals(player.getUsername())) {
                    playersListModel.addElement(p);
                }
            }
        }
    }
    
    private void handleChallenge(Map<String, Object> msg) {
        String challenger = (String) msg.get("challenger");
        int response = JOptionPane.showConfirmDialog(
            mainFrame,
            challenger + " has challenged you to a game!\nDo you accept?",
            "Challenge Received",
            JOptionPane.YES_NO_OPTION
        );
        
        respondToChallenge(challenger, response == JOptionPane.YES_OPTION ? "ACCEPT" : "REJECT");
    }
    
    private void handleChallengeResponse(Map<String, Object> msg) {
        String response = (String) msg.get("response");
        String opponent = (String) msg.get("opponent");
        
        if ("ACCEPT".equals(response)) {
            // Status will be updated to "In Match" when game starts
            showStatus("Status: In Lobby - " + opponent + " accepted your challenge!", Color.GREEN);
        } else {
            showStatus("Status: In Lobby - " + opponent + " rejected your challenge.", Color.ORANGE);
        }
    }
    
    private void handleStartGame(Map<String, Object> msg) {
        currentGameId = (String) msg.get("gameId");
        String player1 = (String) msg.get("player1");
        String player2 = (String) msg.get("player2");
        currentPlayer = (String) msg.get("currentPlayer");
        
        currentOpponent = player1.equals(player.getUsername()) ? player2 : player1;
        inGame = true;
        
        // Initialize board
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                currentBoard[i][j] = ' ';
            }
        }
        
        createGameWindow();
        // Update status to show we're in a match
        if (mainFrame != null && statusLabel != null) {
            showStatus("Status: In Match - Playing against " + currentOpponent, Color.GREEN);
        }
    }
    
    private void handleUpdate(Map<String, Object> msg) {
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        currentPlayer = (String) msg.get("currentPlayer");
        
        if (boardMap != null) {
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                currentBoard[x][y] = entry.getValue().charAt(0);
            }
        }
        
        updateBoardDisplay();
    }
    
    private void handleResult(Map<String, Object> msg) {
        String result = (String) msg.get("result");
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        
        if (boardMap != null) {
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                currentBoard[x][y] = entry.getValue().charAt(0);
            }
        }
        
        inGame = false;
        
        // Disable all board buttons
        if (boardButtons != null) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (boardButtons[i][j] != null) {
                        boardButtons[i][j].setEnabled(false);
                    }
                }
            }
        }
        
        // Update board display
        updateBoardDisplay();
        
        String message;
        Color color;
        if ("WIN".equals(result)) {
            message = "Congratulations! You won!";
            color = Color.GREEN;
            player.incrementWins();
        } else if ("LOSS".equals(result)) {
            message = "You lost. Better luck next time!";
            color = Color.RED;
            player.incrementLosses();
        } else {
            message = "It's a draw!";
            color = Color.ORANGE;
            player.incrementDraws();
        }
        
        // Update stats display in main window
        updateStatsDisplay();
        
        // Update status in game window
        showStatus(message, color);
        currentPlayerLabel.setText(message);
        
        // Update main window status to show we're back in lobby
        if (mainFrame != null && statusLabel != null) {
            showStatus("Status: In Lobby - Game ended", Color.BLUE);
        }
        
        currentGameId = null;
        // Keep currentOpponent for rematch
        
        // Enable buttons IMMEDIATELY on EDT
        SwingUtilities.invokeLater(() -> {
            if (rematchButton != null) {
                rematchButton.setEnabled(true);
                rematchButton.setFocusable(true);
                rematchButton.setVisible(true);
            }
            if (lobbyButton != null) {
                lobbyButton.setEnabled(true);
                lobbyButton.setFocusable(true);
                lobbyButton.setVisible(true);
            }
            
            // Force immediate UI update
            if (gameFrame != null) {
                gameFrame.revalidate();
                gameFrame.repaint();
                gameFrame.toFront();
            }
        });
    }
    
    private void handleRematchRequest(Map<String, Object> msg) {
        String requester = (String) msg.get("requester");
        int response = JOptionPane.showConfirmDialog(
            gameFrame != null ? gameFrame : mainFrame,
            requester + " wants a rematch!\nDo you accept?",
            "Rematch Request",
            JOptionPane.YES_NO_OPTION
        );
        
        respondToRematch(requester, response == JOptionPane.YES_OPTION ? "ACCEPT" : "REJECT");
    }
    
    private void handleRematchResponse(Map<String, Object> msg) {
        String response = (String) msg.get("response");
        String opponent = (String) msg.get("opponent");
        
        if ("ACCEPT".equals(response)) {
            showStatus(opponent + " accepted your rematch request!", Color.GREEN);
        } else {
            showStatus(opponent + " declined your rematch request.", Color.ORANGE);
            // Option to go back to lobby
            if (gameFrame != null) {
                int choice = JOptionPane.showConfirmDialog(
                    gameFrame,
                    "Return to lobby?",
                    "Rematch Declined",
                    JOptionPane.YES_NO_OPTION
                );
                if (choice == JOptionPane.YES_OPTION) {
                    gameFrame.dispose();
                    gameFrame = null;
                    if (mainFrame != null) {
                        mainFrame.setVisible(true);
                    }
                    listPlayers();
                }
            }
        }
    }
    
    private void handleLeaderboard(Map<String, Object> msg) {
        String data = (String) msg.get("data");
        if (data == null || data.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No leaderboard data available");
            return;
        }
        
        String[] entries = data.split("\\|");
        StringBuilder leaderboardText = new StringBuilder();
        leaderboardText.append("<html><body><table border='1' cellpadding='5'>");
        leaderboardText.append("<tr><th>Rank</th><th>Player</th><th>Wins</th><th>Losses</th><th>Draws</th></tr>");
        
        for (String entry : entries) {
            String[] parts = entry.split(",");
            if (parts.length >= 5) {
                leaderboardText.append("<tr>");
                leaderboardText.append("<td>").append(parts[0]).append("</td>");
                leaderboardText.append("<td>").append(parts[1]).append("</td>");
                leaderboardText.append("<td>").append(parts[2]).append("</td>");
                leaderboardText.append("<td>").append(parts[3]).append("</td>");
                leaderboardText.append("<td>").append(parts[4]).append("</td>");
                leaderboardText.append("</tr>");
            }
        }
        
        leaderboardText.append("</table></body></html>");
        
        JLabel label = new JLabel(leaderboardText.toString());
        JScrollPane scrollPane = new JScrollPane(label);
        scrollPane.setPreferredSize(new Dimension(400, 300));
        
        JOptionPane.showMessageDialog(mainFrame, scrollPane, "Leaderboard", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void handleOpponentDisconnected(Map<String, Object> msg) {
        // The disconnected player gets a loss, so we (the remaining player) get a win
        player.incrementWins();
        updateStatsDisplay();
        
        inGame = false;
        
        // Show notification dialog FIRST - this blocks until user clicks OK
        JFrame parentFrame = gameFrame != null ? gameFrame : (mainFrame != null ? mainFrame : loginFrame);
        
        // Bring parent window to front first
        if (parentFrame != null) {
            parentFrame.toFront();
            parentFrame.requestFocus();
        }
        
        JOptionPane dialog = new JOptionPane(
            "Your opponent has disconnected from the match.\n\n" +
            "You win by forfeit!\n\n" +
            "You will be returned to the lobby.",
            JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.DEFAULT_OPTION
        );
        
        JDialog notificationDialog = dialog.createDialog(parentFrame, "Opponent Disconnected");
        notificationDialog.setAlwaysOnTop(true); // Keep dialog on top
        notificationDialog.toFront(); // Bring to front
        notificationDialog.requestFocus(); // Request focus
        notificationDialog.setVisible(true); // Blocks until user clicks OK
        
        // AFTER user clicks OK, close game window and return to lobby
        if (gameFrame != null) {
            gameFrame.dispose();
            gameFrame = null;
            gameStatusLabel = null;
        }
        
        // Show main window (lobby)
        if (mainFrame != null) {
            mainFrame.setVisible(true);
            mainFrame.toFront();
            mainFrame.requestFocus();
        }
        
        // Update main window status
        showStatus("Status: In Lobby - Opponent disconnected. You win by forfeit!", Color.GREEN);
        
        // Refresh player list
        listPlayers();
        
        currentGameId = null;
        currentOpponent = null;
    }
    
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
    
    public void listPlayers() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LIST_PLAYERS");
        sendMessage(Protocol.createMessage(msg));
    }
    
    public void challenge(String opponent) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE");
        msg.put("opponent", opponent);
        sendMessage(Protocol.createMessage(msg));
        showStatus("Status: In Lobby - Challenging " + opponent + "...", Color.BLUE);
    }
    
    public void respondToChallenge(String challenger, String response) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE_RESPONSE");
        msg.put("challenger", challenger);
        msg.put("response", response);
        sendMessage(Protocol.createMessage(msg));
    }
    
    public void makeMove(int x, int y) {
        if (currentGameId == null || !inGame) {
            showStatus("Not in a game", Color.RED);
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
    
    public void requestRematch() {
        if (currentOpponent == null) {
            showStatus("No previous opponent found", Color.RED);
            JOptionPane.showMessageDialog(gameFrame != null ? gameFrame : mainFrame, 
                "No previous opponent found", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (rematchButton != null) {
            rematchButton.setEnabled(false);
        }
        
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_REQUEST");
        msg.put("opponent", currentOpponent);
        sendMessage(Protocol.createMessage(msg));
        showStatus("Requesting rematch with " + currentOpponent + "...", Color.BLUE);
    }
    
    public void respondToRematch(String requester, String response) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_RESPONSE");
        msg.put("opponent", requester);
        msg.put("response", response);
        sendMessage(Protocol.createMessage(msg));
    }
    
    public void requestLeaderboard() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LEADERBOARD");
        sendMessage(Protocol.createMessage(msg));
    }
    
    private void goBackToLobby() {
        // Close game window
        if (gameFrame != null) {
            gameFrame.dispose();
            gameFrame = null;
        }
        
        // Show main window
        if (mainFrame != null) {
            mainFrame.setVisible(true);
            mainFrame.toFront();
            mainFrame.requestFocus();
        }
        
        // Reset game state
        inGame = false;
        currentGameId = null;
        // Keep currentOpponent for potential rematch later
        
        // Refresh player list
        listPlayers();
        
        // Clear any old challenge messages and show lobby status
        showStatus("Status: In Lobby", Color.BLUE);
    }
    
    public void logout() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LOGOUT");
        sendMessage(Protocol.createMessage(msg));
        disconnect();
        
        if (mainFrame != null) mainFrame.dispose();
        if (gameFrame != null) gameFrame.dispose();
        System.exit(0);
    }
    
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
     * Handle server disconnection - show notification and shutdown
     */
    public void handleServerDisconnection(String reason) {
        SwingUtilities.invokeLater(() -> {
            // Stop the listener first to prevent further messages
            if (listener != null) {
                listener.stopListening();
            }
            
            // Bring any open window to front first
            JFrame parentFrame = null;
            if (mainFrame != null && mainFrame.isVisible()) {
                mainFrame.toFront();
                mainFrame.requestFocus();
                parentFrame = mainFrame;
            } else if (gameFrame != null && gameFrame.isVisible()) {
                gameFrame.toFront();
                gameFrame.requestFocus();
                parentFrame = gameFrame;
            } else if (loginFrame != null && loginFrame.isVisible()) {
                loginFrame.toFront();
                loginFrame.requestFocus();
                parentFrame = loginFrame;
            }
            
            // Show error notification FIRST - this blocks until user clicks OK
            JOptionPane dialog = new JOptionPane(
                "Connection to server lost.\n\n" + 
                (reason != null ? reason : "The server connection has been closed.") + 
                "\n\nThe application will now close.",
                JOptionPane.ERROR_MESSAGE,
                JOptionPane.DEFAULT_OPTION
            );
            
            JDialog errorDialog = dialog.createDialog(parentFrame, "Server Disconnected");
            errorDialog.setAlwaysOnTop(true); // Keep dialog on top
            errorDialog.toFront(); // Bring to front
            errorDialog.requestFocus(); // Request focus
            errorDialog.setVisible(true); // Show and block until user clicks OK
            
            // AFTER user clicks OK, close all windows
            if (gameFrame != null) {
                gameFrame.dispose();
            }
            if (mainFrame != null) {
                mainFrame.dispose();
            }
            if (registrationDialog != null) {
                registrationDialog.dispose();
            }
            if (loginFrame != null) {
                loginFrame.dispose();
            }
            
            // Disconnect from server
            disconnect();
            
            // Exit application
            System.exit(0);
        });
    }
    
    private void showStatus(String message, Color color) {
        // Don't show in login window if we're registering - show in registration dialog instead
        if (isRegistering && registrationStatusLabel != null && registrationDialog != null) {
            SwingUtilities.invokeLater(() -> {
                registrationStatusLabel.setText(message);
                registrationStatusLabel.setForeground(color);
            });
            return;
        }
        
        // Update main window status
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
        // Update game window status if it exists
        if (gameStatusLabel != null) {
            gameStatusLabel.setText(message);
            gameStatusLabel.setForeground(color);
        }
    }
    
    private void updateStatsDisplay() {
        if (statsLabel != null) {
            statsLabel.setText(String.format("Wins: %d | Losses: %d | Draws: %d", 
                player.getWins(), player.getLosses(), player.getDraws()));
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new GameClientGUI();
        });
    }
}

