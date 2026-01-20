import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;
import java.util.Map;

/**
 * GameClientGUI.java - GUI version of the game client using Swing
 * this class creates the graphical interface for the tic-tac-toe client
 * it handles login, registration, player list display, and game UI
 */
public class GameClientGUI {
    // server connection details - where to connect to play the game
    private static final String SERVER_HOST = "localhost"; // server is on this computer
    private static final int SERVER_PORT = 12345; // port number where server listens
    
    // network communication objects - for talking to the server
    private Socket socket; // connection to the server
    private BufferedReader in; // for reading messages from server
    private PrintWriter out; // for sending messages to server
    private ServerListenerGUI listener; // background thread that listens for server messages
    private Player player; // the current logged-in player's info (username, stats, etc)
    
    // GUI Components - the visual windows and elements we show to the user
    private JFrame loginFrame; // the window where user logs in or registers
    private JFrame mainFrame; // the lobby window showing online players
    private JFrame gameFrame; // the window where the actual game is played
    private JTextField usernameField; // text box where user enters their username for login
    private JPasswordField passwordField; // password box where user enters their password
    private DefaultListModel<String> playersListModel; // data model that holds the list of online players
    private JList<String> playersList; // displays list of online players the user can challenge
    private JButton[][] boardButtons; // 3x3 grid of buttons that make up the game board
    private JLabel statusLabel; // shows messages in main lobby window (like "Game started" or "Challenge sent")
    private JLabel gameStatusLabel; // shows messages in game window (like "Your turn" or "Game ended")
    private JLabel currentPlayerLabel; // shows whose turn it is during the game
    private JLabel statsLabel; // shows player's statistics (wins, losses, draws) in main window
    private JDialog registrationDialog; // the dialog window for creating a new account
    private JLabel registrationStatusLabel; // shows messages during registration (like "Username taken" or "Registration successful")
    private boolean isRegistering; // flag to track if we're in registration mode (helps route status messages correctly)
    private String currentGameId; // unique ID for the current game session (null if not in a game)
    private String currentOpponent; // username of the player we're playing against
    private char[][] currentBoard; // 2d array representing the 3x3 game board (stores X, O, or space)
    private String currentPlayer; // whose turn it is: either the current player's username or opponent's username
    private boolean inGame; // flag to track if we're currently playing a game
    
    /**
     * constructor - initializes the GUI client when the application starts
     * sets up the player object, game board, and creates the login window
     */
    public GameClientGUI() {
        player = new Player(); // create empty player object (will be filled in after login)
        currentBoard = new char[3][3]; // create empty 3x3 game board
        inGame = false; // we're not in a game yet, just starting
        playersListModel = new DefaultListModel<>(); // create empty list for online players
        
        // show the login window first thing
        createLoginWindow();
    }
    
    /**
     * createLoginWindow - creates and displays the login/register screen
     * this is the first window user sees when they start the app
     */
    private void createLoginWindow() {
        loginFrame = new JFrame("Tic-Tac-Toe - Login"); // create the window
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // close app when window closes
        loginFrame.setSize(350, 250); // make it 350 pixels wide and 250 tall
        loginFrame.setLocationRelativeTo(null); // put window in center of screen
        loginFrame.setLayout(new BorderLayout(10, 10)); // use border layout with 10 pixel gaps
        
        // create main panel inside the window using grid bag layout (lets us position things precisely)
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints(); // controls how things are positioned
        gbc.insets = new Insets(5, 5, 5, 5); // put 5 pixels of space around each element
        
        // TITLE LABEL at top
        gbc.gridx = 0; // column 0
        gbc.gridy = 0; // row 0
        gbc.gridwidth = 2; // take up 2 columns
        JLabel titleLabel = new JLabel("Tic-Tac-Toe Game", JLabel.CENTER); // title text
        titleLabel.setFont(new Font("Arial", Font.BOLD, 18)); // make it big and bold
        mainPanel.add(titleLabel, gbc); // add to panel
        
        // USERNAME FIELD
        gbc.gridwidth = 1; // back to 1 column
        gbc.gridy = 1; // move down to row 1
        gbc.gridx = 0; // column 0 (left side)
        mainPanel.add(new JLabel("Username:"), gbc); // label on left
        gbc.gridx = 1; // column 1 (right side)
        gbc.fill = GridBagConstraints.HORIZONTAL; // make text field stretch horizontally
        gbc.weightx = 1.0; // let it take up extra space
        usernameField = new JTextField(15); // create text input box (15 chars wide)
        mainPanel.add(usernameField, gbc); // add to panel
        
        // PASSWORD FIELD with show/hide checkbox
        gbc.gridy = 2; // row 2
        gbc.gridx = 0; // left side
        gbc.fill = GridBagConstraints.NONE; // don't stretch this label
        gbc.weightx = 0; // don't give it extra space
        mainPanel.add(new JLabel("Password:"), gbc); // label
        gbc.gridx = 1; // right side
        gbc.fill = GridBagConstraints.HORIZONTAL; // stretch text field
        gbc.weightx = 1.0; // give it extra space
        passwordField = new JPasswordField(15); // create password box (shows dots instead of text)
        JCheckBox showPasswordCheck = new JCheckBox("Show"); // checkbox to toggle show/hide
        showPasswordCheck.addActionListener(e -> {
            // when checkbox is clicked, toggle between showing dots or actual password
            passwordField.setEchoChar(showPasswordCheck.isSelected() ? (char) 0 : '\u2022');
        });
        JPanel passwordPanel = new JPanel(new BorderLayout(5, 0)); // create sub-panel for password + checkbox
        passwordPanel.add(passwordField, BorderLayout.CENTER); // password field takes up main space
        passwordPanel.add(showPasswordCheck, BorderLayout.EAST); // checkbox on right side
        mainPanel.add(passwordPanel, gbc); // add the whole thing to main panel
        
        // LOGIN AND REGISTER BUTTONS
        gbc.gridy = 3; // row 3
        gbc.gridx = 0; // left side
        gbc.gridwidth = 1; // 1 column
        gbc.fill = GridBagConstraints.NONE; // don't stretch
        gbc.weightx = 0.5; // split space with right button
        JButton registerButton = new JButton("Register"); // "Register" button
        registerButton.addActionListener(e -> showRegistrationDialog()); // when clicked, show registration window
        mainPanel.add(registerButton, gbc);
        
        gbc.gridx = 1; // right side
        JButton loginButton = new JButton("Login"); // "Login" button
        loginButton.addActionListener(e -> handleLogin()); // when clicked, try to login
        mainPanel.add(loginButton, gbc);
        
        // STATUS LABEL (shows error/success messages)
        gbc.gridy = 4; // row 4 (bottom)
        gbc.gridx = 0; // span both columns
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL; // stretch horizontally
        statusLabel = new JLabel(" ", JLabel.CENTER); // empty label initially
        statusLabel.setForeground(Color.RED); // message text will be red (for errors)
        mainPanel.add(statusLabel, gbc);
        
        loginFrame.add(mainPanel, BorderLayout.CENTER); // add main panel to window
        loginFrame.setVisible(true); // show the window
        
        // allow user to press Enter in password field to login (instead of clicking button)
        passwordField.addActionListener(e -> handleLogin());
    }
    
    /**
     * showRegistrationDialog - creates and shows the window for creating a new account
     * lets user enter username, password, name, and email
     */
    private void showRegistrationDialog() {
        // create a dialog window (looks like a popup on top of login window)
        registrationDialog = new JDialog(loginFrame, "Register New Account", true); // true = blocks interaction with parent window
        registrationDialog.setSize(400, 320); // make it fairly large to fit all fields
        registrationDialog.setMinimumSize(new Dimension(400, 320)); // prevent user from shrinking it too much
        registrationDialog.setResizable(false); // don't let user resize manually
        registrationDialog.setLocationRelativeTo(loginFrame); // center it on the login window
        registrationDialog.setLayout(new BorderLayout(10, 10)); // use border layout
        
        // create main panel with grid bag layout
        JPanel mainPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5); // 5 pixel padding
        
        // TITLE
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        JLabel titleLabel = new JLabel("Registration", JLabel.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        mainPanel.add(titleLabel, gbc);
        
        // USERNAME INPUT
        gbc.gridwidth = 1;
        gbc.gridy = 1;
        gbc.gridx = 0;
        mainPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        JTextField regUsernameField = new JTextField(20);
        mainPanel.add(regUsernameField, gbc);
        
        // PASSWORD INPUT with show/hide toggle
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
        
        // NAME INPUT
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
        
        // EMAIL INPUT
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
        
        // STATUS LABEL (shows messages during registration)
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        registrationStatusLabel = new JLabel(" ", JLabel.CENTER);
        registrationStatusLabel.setForeground(Color.RED);
        mainPanel.add(registrationStatusLabel, gbc);
        
        // CREATE BUTTONS
        JPanel buttonPanel = new JPanel(new FlowLayout()); // simple layout for buttons
        JButton submitButton = new JButton("Register"); // "Register" button
        JButton cancelButton = new JButton("Cancel"); // "Cancel" button
        
        // store references for use in event handlers
        final JDialog dialogRef = registrationDialog;
        final JLabel statusRef = registrationStatusLabel;
        
        // when user clicks "Register" button
        submitButton.addActionListener(e -> {
            // get what user typed in each field
            String username = regUsernameField.getText().trim(); // trim removes extra spaces
            String password = new String(regPasswordField.getPassword()); // get password
            String name = regNameField.getText().trim();
            String email = regEmailField.getText().trim();
            
            // check if all required fields are filled in
            if (username.isEmpty() || password.isEmpty()) {
                statusRef.setText("Username and password are required"); // show error
                statusRef.setForeground(Color.RED);
                return; // stop here, don't try to register
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
            
            // try to connect to server if not already connected
            if (!connect()) {
                statusRef.setText("Failed to connect to server");
                statusRef.setForeground(Color.RED);
                return;
            }
            
            // set flag so we know we're trying to register (not login)
            isRegistering = true;
            
            // create registration message
            Map<String, Object> msg = new java.util.HashMap<>();
            msg.put("type", "REGISTER"); // message type
            msg.put("username", username);
            msg.put("password", password);
            msg.put("name", name);
            msg.put("email", email);
            sendMessage(Protocol.createMessage(msg)); // send to server
            
            // show "please wait" message
            statusRef.setText("Registering...");
            statusRef.setForeground(Color.BLUE);
        });
        
        // when user clicks "Cancel" button
        cancelButton.addActionListener(e -> {
            isRegistering = false; // not registering anymore
            dialogRef.dispose(); // close the dialog
        });
        
        // add buttons to button panel
        buttonPanel.add(submitButton);
        buttonPanel.add(cancelButton);
        
        // add panels to dialog
        registrationDialog.add(mainPanel, BorderLayout.CENTER); // main form in center
        registrationDialog.add(buttonPanel, BorderLayout.SOUTH); // buttons at bottom
        registrationDialog.setVisible(true); // show the dialog
    }
    
    
    /**
     * handleLogin - sends login request to server with username and password
     * gets called when user clicks Login button or presses Enter
     */
    private void handleLogin() {
        // get what user typed
        String username = usernameField.getText().trim(); // .trim() removes extra spaces before/after
        String password = new String(passwordField.getPassword()); // get password securely
        
        // check if both fields are filled
        if (username.isEmpty() || password.isEmpty()) {
            showStatus("Please enter username and password", Color.RED); // show error in red
            return; // stop, don't continue
        }
        
        // try to connect to server
        if (!connect()) {
            showStatus("Failed to connect to server", Color.RED);
            return;
        }
        
        // create login message to send to server
        Map<String, Object> msg = new java.util.HashMap<>(); // create empty message map
        msg.put("type", "LOGIN"); // this is a login request
        msg.put("username", username);
        msg.put("password", password);
        sendMessage(Protocol.createMessage(msg)); // convert to string format and send
    }
    
    /**
     * connect - establishes connection to the server
     * creates socket and streams for communication
     * starts background listener thread to receive messages
     * returns true if successful, false if failed
     */
    private boolean connect() {
        // check if already connected
        if (socket != null && !socket.isClosed()) {
            return true; // already have a good connection
        }
        
        try {
            // create socket to connect to server
            socket = new Socket(SERVER_HOST, SERVER_PORT); // connect to localhost:12345
            in = new BufferedReader(new InputStreamReader(socket.getInputStream())); // set up input stream (for reading)
            out = new PrintWriter(socket.getOutputStream(), true); // set up output stream (for writing). "true" = auto-flush
            
            // start background thread that listens for messages from server
            listener = new ServerListenerGUI(in, this); // pass input stream and reference to this GUI object
            listener.start(); // start the thread
            
            return true; // success!
        } catch (IOException e) {
            // connection failed
            showStatus("Connection error: " + e.getMessage(), Color.RED);
            return false;
        }
    }
    
    /**
     * handleServerMessage - processes messages received from the server
     * decides what to do based on message type (login success, game update, etc)
     * runs on a special "EDT" thread that's safe for updating the GUI
     */
    public void handleServerMessage(String message) {
        // SwingUtilities.invokeLater is important! it puts GUI updates on the EDT (Event Dispatch Thread)
        // GUI updates MUST happen on EDT or the program might crash or act weird
        SwingUtilities.invokeLater(() -> {
            // parse the message string back into a map
            Map<String, Object> msg = Protocol.parseMessage(message);
            String type = (String) msg.get("type"); // get the message type
            
            if (type == null) {
                return; // invalid message, ignore it
            }
            
            // use switch to handle different message types
            switch (type) {
                // user successfully logged in
                case "LOGIN_SUCCESS":
                    handleLoginSuccess(msg);
                    break;
                    
                // server says success (could be registration or other operation)
                case "SUCCESS":
                    String successMsg = (String) msg.get("message");
                    if (successMsg != null && successMsg.contains("Registration successful")) {
                        // registration worked! close the registration dialog
                        isRegistering = false; // not registering anymore
                        if (registrationDialog != null) {
                            registrationDialog.dispose(); // close the dialog window
                            registrationDialog = null; // clear the reference
                            registrationStatusLabel = null;
                        }
                        // show success in login window status
                        if (loginFrame != null && statusLabel != null) {
                            showStatus(successMsg + " You can now login.", Color.GREEN);
                        }
                        // disconnect from server since we just finished registering
                        disconnect();
                    } else {
                        // other success message, only show if not in registration
                        if (!isRegistering) {
                            showStatus(successMsg, Color.GREEN);
                        }
                    }
                    break;
                    
                // server says error
                case "ERROR":
                    String errorMsg = (String) msg.get("message");
                    // if we're registering, show error in registration dialog
                    if (isRegistering && registrationStatusLabel != null && registrationDialog != null) {
                        SwingUtilities.invokeLater(() -> {
                            registrationStatusLabel.setText(errorMsg);
                            registrationStatusLabel.setForeground(Color.RED);
                        });
                        isRegistering = false; // stop registering
                    } else {
                        // show error in login or main window
                        showStatus(errorMsg, Color.RED);
                    }
                    break;
                    
                // server sent list of online players
                case "PLAYERS_LIST":
                    handlePlayersList(msg);
                    break;
                    
                // another player challenged us
                case "CHALLENGE":
                    handleChallenge(msg);
                    break;
                    
                // server responded to our challenge
                case "CHALLENGE_RESPONSE":
                    handleChallengeResponse(msg);
                    break;
                    
                // game is starting
                case "START_GAME":
                    handleStartGame(msg);
                    break;
                    
                // game state was updated (another player made a move)
                case "UPDATE":
                    handleUpdate(msg);
                    break;
                    
                // game ended (someone won or it's a draw)
                case "RESULT":
                    handleResult(msg);
                    break;
                    
                // the opponent disconnected from the game
                case "OPPONENT_DISCONNECTED":
                    handleOpponentDisconnected(msg);
                    break;
                    
                // opponent wants a rematch
                case "REMATCH_REQUEST":
                    handleRematchRequest(msg);
                    break;
                    
                // server responded to our rematch request
                case "REMATCH_RESPONSE":
                    handleRematchResponse(msg);
                    break;
                    
                // leaderboard data from server
                case "LEADERBOARD":
                    handleLeaderboard(msg);
                    break;
            }
        });
    }
    
    /**
     * handleLoginSuccess - called when server says login was successful
     * fills in player data and opens the main lobby window
     */
    private void handleLoginSuccess(Map<String, Object> msg) {
        // get the player data from the server message
        player.setUsername((String) msg.get("username"));
        player.setStats( // set wins, losses, draws
            Integer.parseInt((String) msg.get("wins")),
            Integer.parseInt((String) msg.get("losses")),
            Integer.parseInt((String) msg.get("draws"))
        );
        player.setName((String) msg.get("name"));
        player.setEmail((String) msg.get("email"));
        // server doesn't send password for security reasons, so use placeholder
        player.setPassword("***"); // show that password is not available
        player.setAuthenticated(true); // mark as logged in
        
        // hide login window
        loginFrame.setVisible(false);
        
        // create and show main lobby window
        createMainWindow();
        
        // ask server for list of online players
        listPlayers();
    }
    
    /**
     * createMainWindow - creates and shows the main lobby window
     * displays user profile, statistics, and list of online players to challenge
     */
    private void createMainWindow() {
        // create the main window
        mainFrame = new JFrame("Tic-Tac-Toe - " + player.getUsername()); // show username in title
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setSize(500, 600);
        mainFrame.setLocationRelativeTo(null);
        
        // create main panel with border layout (border layout has 5 regions: north, south, east, west, center)
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // 10 pixel margin on all sides
        
        // TOP PANEL - user profile and control buttons
        JPanel topPanel = new JPanel(new BorderLayout(5, 5));
        
        // profile panel - shows user info
        JPanel profilePanel = new JPanel(new GridBagLayout());
        profilePanel.setBorder(BorderFactory.createTitledBorder("Your Profile")); // border with label
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST; // align to left
        
        // Username row
        gbc.gridx = 0; gbc.gridy = 0;
        profilePanel.add(new JLabel("Username:"), gbc); // label
        gbc.gridx = 1;
        profilePanel.add(new JLabel(player.getUsername()), gbc); // value
        
        // Name row
        gbc.gridx = 0; gbc.gridy = 1;
        profilePanel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        profilePanel.add(new JLabel(player.getName() != null && !player.getName().isEmpty() ? player.getName() : "N/A"), gbc);
        
        // Email row
        gbc.gridx = 0; gbc.gridy = 2;
        profilePanel.add(new JLabel("Email:"), gbc);
        gbc.gridx = 1;
        profilePanel.add(new JLabel(player.getEmail() != null && !player.getEmail().isEmpty() ? player.getEmail() : "N/A"), gbc);
        
        // Statistics row
        gbc.gridx = 0; gbc.gridy = 3;
        profilePanel.add(new JLabel("Statistics:"), gbc);
        gbc.gridx = 1;
        statsLabel = new JLabel(String.format("Wins: %d | Losses: %d | Draws: %d", 
            player.getWins(), player.getLosses(), player.getDraws())); // show stats
        profilePanel.add(statsLabel, gbc);
        
        topPanel.add(profilePanel, BorderLayout.CENTER); // profile on left side of top panel
        
        // control buttons panel - refresh players, leaderboard, logout
        JPanel controlPanel = new JPanel(new FlowLayout()); // flow layout puts things in a row
        JButton refreshButton = new JButton("Refresh Players");
        refreshButton.addActionListener(e -> listPlayers()); // refresh when clicked
        JButton leaderboardButton = new JButton("Leaderboard");
        leaderboardButton.addActionListener(e -> requestLeaderboard()); // show leaderboard when clicked
        JButton logoutButton = new JButton("Logout");
        logoutButton.addActionListener(e -> logout()); // logout when clicked
        controlPanel.add(refreshButton);
        controlPanel.add(leaderboardButton);
        controlPanel.add(logoutButton);
        topPanel.add(controlPanel, BorderLayout.SOUTH); // buttons below profile
        
        mainPanel.add(topPanel, BorderLayout.NORTH); // put top panel at top
        
        // CENTER PANEL - list of online players
        JPanel playersPanel = new JPanel(new BorderLayout());
        playersPanel.setBorder(BorderFactory.createTitledBorder("Online Players"));
        playersList = new JList<>(playersListModel); // create list using our player list model
        playersList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // can only select one player at a time
        JScrollPane scrollPane = new JScrollPane(playersList); // add scroll bars if list is long
        playersPanel.add(scrollPane, BorderLayout.CENTER); // list takes up main space
        
        // challenge button - sends challenge to selected player
        JButton challengeButton = new JButton("Challenge Selected Player");
        challengeButton.addActionListener(e -> {
            String selected = playersList.getSelectedValue(); // get selected player name
            if (selected != null) {
                challenge(selected); // send challenge
            } else {
                JOptionPane.showMessageDialog(mainFrame, "Please select a player to challenge"); // show error if nothing selected
            }
        });
        playersPanel.add(challengeButton, BorderLayout.SOUTH); // button below list
        
        mainPanel.add(playersPanel, BorderLayout.CENTER); // put players panel in center of main panel
        
        // STATUS AREA at bottom
        statusLabel = new JLabel("Status: In Lobby", JLabel.CENTER); // initial status message
        statusLabel.setBorder(BorderFactory.createLoweredBevelBorder()); // fancy border
        mainPanel.add(statusLabel, BorderLayout.SOUTH); // put at bottom
        
        mainFrame.add(mainPanel); // add main panel to window
        mainFrame.setVisible(true); // show the window
    }
    
    /**
     * createGameWindow - creates and shows the window where the actual game is played
     * displays the 3x3 board, current player indicator, and game control buttons
     */
    private void createGameWindow() {
        // close old game window if it exists (from a previous game)
        if (gameFrame != null) {
            gameFrame.dispose();
        }
        
        // create new game window
        gameFrame = new JFrame("Tic-Tac-Toe - vs " + currentOpponent); // show opponent name in title
        gameFrame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // don't close on X button (we'll handle closing ourselves)
        gameFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            // this method runs when user tries to close the window
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                // if in an active game, notify server that player is leaving
                if (inGame && currentGameId != null) {
                    Map<String, Object> msg = new java.util.HashMap<>();
                    msg.put("type", "LEAVE_GAME");
                    msg.put("gameId", currentGameId);
                    sendMessage(Protocol.createMessage(msg));
                }
                
                // close game window
                if (gameFrame != null) {
                    gameFrame.dispose();
                    gameFrame = null;
                }
                
                // show main lobby window
                if (mainFrame != null) {
                    mainFrame.setVisible(true);
                    mainFrame.toFront();
                }
                
                // update status
                showStatus("Status: In Lobby", Color.BLUE);
                
                // refresh player list
                listPlayers();
                
                // reset game state
                inGame = false;
                currentGameId = null;
                currentOpponent = null;
            }
        });
        gameFrame.setSize(400, 550);
        gameFrame.setLocationRelativeTo(null);
        
        // create main panel
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // TOP - current player label shows whose turn it is
        currentPlayerLabel = new JLabel("Your turn!", JLabel.CENTER);
        currentPlayerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        currentPlayerLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        mainPanel.add(currentPlayerLabel, BorderLayout.NORTH);
        
        // CENTER - the 3x3 game board
        JPanel boardPanel = new JPanel(new GridLayout(3, 3, 5, 5)); // 3x3 grid with 5 pixel gaps
        boardPanel.setBorder(BorderFactory.createTitledBorder("Game Board"));
        boardButtons = new JButton[3][3]; // create array to store the 9 buttons
        
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                final int x = i; // final so can use in lambda
                final int y = j; // final so can use in lambda
                JButton button = new JButton(" "); // create button
                button.setFont(new Font("Arial", Font.BOLD, 48)); // big bold font
                button.setPreferredSize(new Dimension(100, 100));
                button.addActionListener(e -> makeMove(x, y)); // when clicked, make a move at this position
                boardButtons[i][j] = button;
                boardPanel.add(button);
            }
        }
        
        mainPanel.add(boardPanel, BorderLayout.CENTER);
        
        // BOTTOM - rematch and lobby buttons (enabled after game ends)
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        buttonPanel.setBorder(BorderFactory.createTitledBorder("Game Options"));
        buttonPanel.setBackground(Color.WHITE);
        
        // rematch button - ask opponent to play again
        rematchButton = new JButton("Request Rematch");
        rematchButton.setEnabled(false); // disabled until game ends
        rematchButton.setPreferredSize(new Dimension(160, 35));
        rematchButton.setFont(new Font("Arial", Font.BOLD, 12));
        rematchButton.addActionListener(e -> {
            if (currentOpponent != null) {
                requestRematch(); // send rematch request to opponent
            } else {
                showStatus("No opponent to rematch", Color.RED);
            }
        });
        
        // back to lobby button
        lobbyButton = new JButton("Back to Lobby");
        lobbyButton.setEnabled(false); // disabled until game ends
        lobbyButton.setPreferredSize(new Dimension(160, 35));
        lobbyButton.setFont(new Font("Arial", Font.BOLD, 12));
        lobbyButton.addActionListener(e -> goBackToLobby()); // close game and go back
        
        buttonPanel.add(rematchButton);
        buttonPanel.add(lobbyButton);
        
        // status label - shows messages during game
        gameStatusLabel = new JLabel("Game in progress", JLabel.CENTER);
        gameStatusLabel.setForeground(Color.BLUE);
        gameStatusLabel.setBorder(BorderFactory.createLoweredBevelBorder());
        
        JPanel southPanel = new JPanel(new BorderLayout(5, 5));
        southPanel.add(buttonPanel, BorderLayout.NORTH);
        southPanel.add(gameStatusLabel, BorderLayout.SOUTH);
        mainPanel.add(southPanel, BorderLayout.SOUTH);
        
        gameFrame.add(mainPanel);
        gameFrame.setVisible(true);
        
        // display the board
        updateBoardDisplay();
    }
    
    private JButton rematchButton; // button for requesting rematch after game ends
    private JButton lobbyButton; // button for going back to lobby
    
    /**
     * updateBoardDisplay - updates the visual board to match current game state
     * shows X, O, or empty for each cell
     * disables cells where moves already were made
     * shows whose turn it is (changes color and text)
     */
    private void updateBoardDisplay() {
        if (boardButtons == null) return; // no board yet
        
        // update each button on the board
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                char cell = currentBoard[i][j]; // get what's in this cell (X, O, or space)
                JButton button = boardButtons[i][j];
                
                if (cell == ' ') {
                    // empty cell
                    button.setText(" ");
                    // enable button if it's our turn and we're in the game
                    button.setEnabled(inGame && currentPlayer != null && currentPlayer.equals(player.getUsername()));
                } else {
                    // cell has X or O already
                    button.setText(String.valueOf(cell));
                    button.setEnabled(false); // can't click here anymore
                }
            }
        }
        
        // update the "whose turn" label at top
        if (currentPlayerLabel != null && currentPlayer != null) {
            if (currentPlayer.equals(player.getUsername())) {
                // it's our turn
                currentPlayerLabel.setText("Your turn!");
                currentPlayerLabel.setForeground(Color.GREEN);
            } else {
                // it's opponent's turn
                currentPlayerLabel.setText(currentOpponent + "'s turn");
                currentPlayerLabel.setForeground(Color.RED);
            }
        }
    }
    
    /**
     * handlePlayersList - updates the list of online players shown in main window
     * removes current player from the list (can't challenge yourself)
     */
    private void handlePlayersList(Map<String, Object> msg) {
        String playersStr = (String) msg.get("players");
        playersListModel.clear(); // empty the current list
        if (playersStr != null && !playersStr.isEmpty()) {
            String[] players = playersStr.split(","); // split comma-separated names
            for (String p : players) {
                // don't show ourselves in the list
                if (!p.equals(player.getUsername())) {
                    playersListModel.addElement(p); // add player to list
                }
            }
        }
    }
    
    /**
     * handleChallenge - process incoming challenge from another player
     * shows dialog asking if we accept or reject
     */
    private void handleChallenge(Map<String, Object> msg) {
        String challenger = (String) msg.get("challenger");
        // show confirmation dialog
        int response = JOptionPane.showConfirmDialog(
            mainFrame,
            challenger + " has challenged you to a game!\nDo you accept?",
            "Challenge Received",
            JOptionPane.YES_NO_OPTION
        );
        
        // send response back to challenger
        respondToChallenge(challenger, response == JOptionPane.YES_OPTION ? "ACCEPT" : "REJECT");
    }
    
    /**
     * handleChallengeResponse - process response to our challenge
     * either accepted (game starting soon) or rejected (stay in lobby)
     */
    private void handleChallengeResponse(Map<String, Object> msg) {
        String response = (String) msg.get("response");
        String opponent = (String) msg.get("opponent");
        
        if ("ACCEPT".equals(response)) {
            // accepted! show success message (game will start with START_GAME message)
            showStatus("Status: In Lobby - " + opponent + " accepted your challenge!", Color.GREEN);
        } else {
            // rejected
            showStatus("Status: In Lobby - " + opponent + " rejected your challenge.", Color.ORANGE);
        }
    }
    
    /**
     * handleStartGame - game is starting! set up game state and show game window
     */
    private void handleStartGame(Map<String, Object> msg) {
        currentGameId = (String) msg.get("gameId");
        String player1 = (String) msg.get("player1");
        String player2 = (String) msg.get("player2");
        currentPlayer = (String) msg.get("currentPlayer");
        
        // figure out who the opponent is
        currentOpponent = player1.equals(player.getUsername()) ? player2 : player1;
        inGame = true; // we're in a game now!
        
        // clear board and fill with empty spaces
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                currentBoard[i][j] = ' ';
            }
        }
        
        // show game window
        createGameWindow();
        
        // update main window to show we're playing
        if (mainFrame != null && statusLabel != null) {
            showStatus("Status: In Match - Playing against " + currentOpponent, Color.GREEN);
        }
    }
    
    /**
     * handleUpdate - process game board update when opponent makes a move
     * updates our local copy of the board and refreshes display
     */
    private void handleUpdate(Map<String, Object> msg) {
        // get the board state from server
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        currentPlayer = (String) msg.get("currentPlayer"); // whose turn is it now?
        
        // copy board data to our local board
        if (boardMap != null) {
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                String[] coords = entry.getKey().split(","); // parse coordinates like "1,2"
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                currentBoard[x][y] = entry.getValue().charAt(0); // put X or O in the cell
            }
        }
        
        // redraw the board on screen
        updateBoardDisplay();
    }
    
    /**
     * handleResult - process end of game (win, loss, or draw)
     * updates player stats, shows result message, enables rematch/lobby buttons
     */
    private void handleResult(Map<String, Object> msg) {
        String result = (String) msg.get("result");
        // get final board state
        @SuppressWarnings("unchecked")
        Map<String, String> boardMap = (Map<String, String>) msg.get("board");
        
        // copy final board state
        if (boardMap != null) {
            for (Map.Entry<String, String> entry : boardMap.entrySet()) {
                String[] coords = entry.getKey().split(",");
                int x = Integer.parseInt(coords[0]);
                int y = Integer.parseInt(coords[1]);
                currentBoard[x][y] = entry.getValue().charAt(0);
            }
        }
        
        inGame = false; // game is over
        
        // disable all board buttons so user can't click anymore
        if (boardButtons != null) {
            for (int i = 0; i < 3; i++) {
                for (int j = 0; j < 3; j++) {
                    if (boardButtons[i][j] != null) {
                        boardButtons[i][j].setEnabled(false);
                    }
                }
            }
        }
        
        // redraw board with final state
        updateBoardDisplay();
        
        // figure out result message and update player stats
        String message;
        Color color;
        if ("WIN".equals(result)) {
            message = "Congratulations! You won!";
            color = Color.GREEN;
            player.incrementWins(); // add to our win count
        } else if ("LOSS".equals(result)) {
            message = "You lost. Better luck next time!";
            color = Color.RED;
            player.incrementLosses(); // add to our loss count
        } else {
            // DRAW
            message = "It's a draw!";
            color = Color.ORANGE;
            player.incrementDraws(); // add to our draw count
        }
        
        // refresh the stats display in main window
        updateStatsDisplay();
        
        // show result message in game window
        showStatus(message, color);
        currentPlayerLabel.setText(message);
        
        // update main window to show we're back in lobby (when game was actually our game)
        if (mainFrame != null && statusLabel != null) {
            showStatus("Status: In Lobby - Game ended", Color.BLUE);
        }
        
        currentGameId = null; // game is over, no more game ID
        // keep currentOpponent so can request rematch later
        
        // enable rematch and lobby buttons so user can do something
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
            
            // redraw window to show buttons
            if (gameFrame != null) {
                gameFrame.revalidate();
                gameFrame.repaint();
                gameFrame.toFront();
            }
        });
    }
    
    /**
     * handleRematchRequest - opponent wants to play again
     * show dialog asking if we accept
     */
    private void handleRematchRequest(Map<String, Object> msg) {
        String requester = (String) msg.get("requester");
        int response = JOptionPane.showConfirmDialog(
            gameFrame != null ? gameFrame : mainFrame,
            requester + " wants a rematch!\nDo you accept?",
            "Rematch Request",
            JOptionPane.YES_NO_OPTION
        );
        
        // send response
        respondToRematch(requester, response == JOptionPane.YES_OPTION ? "ACCEPT" : "REJECT");
    }
    
    /**
     * handleRematchResponse - opponent answered our rematch request
     */
    private void handleRematchResponse(Map<String, Object> msg) {
        String response = (String) msg.get("response");
        String opponent = (String) msg.get("opponent");
        
        if ("ACCEPT".equals(response)) {
            // accepted! rematch will start
            showStatus(opponent + " accepted your rematch request!", Color.GREEN);
        } else {
            // rejected
            showStatus(opponent + " declined your rematch request.", Color.ORANGE);
            // ask user if they want to go back to lobby
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
    
    /**
     * handleLeaderboard - server sent leaderboard data (top players by wins)
     * display it in a nice formatted table
     */
    private void handleLeaderboard(Map<String, Object> msg) {
        String data = (String) msg.get("data");
        if (data == null || data.isEmpty()) {
            JOptionPane.showMessageDialog(mainFrame, "No leaderboard data available");
            return;
        }
        
        String[] entries = data.split("\\|"); // split entries by pipe character
        StringBuilder leaderboardText = new StringBuilder();
        // create HTML table
        leaderboardText.append("<html><body><table border='1' cellpadding='5'>");
        leaderboardText.append("<tr><th>Rank</th><th>Player</th><th>Wins</th><th>Losses</th><th>Draws</th></tr>");
        
        // add each player row
        for (String entry : entries) {
            String[] parts = entry.split(","); // split player data by commas
            if (parts.length >= 5) {
                leaderboardText.append("<tr>");
                leaderboardText.append("<td>").append(parts[0]).append("</td>"); // rank
                leaderboardText.append("<td>").append(parts[1]).append("</td>"); // username
                leaderboardText.append("<td>").append(parts[2]).append("</td>"); // wins
                leaderboardText.append("<td>").append(parts[3]).append("</td>"); // losses
                leaderboardText.append("<td>").append(parts[4]).append("</td>"); // draws
                leaderboardText.append("</tr>");
            }
        }
        
        leaderboardText.append("</table></body></html>");
        
        // show leaderboard in a nice formatted dialog
        JLabel label = new JLabel(leaderboardText.toString());
        JScrollPane scrollPane = new JScrollPane(label); // add scroll if table is long
        scrollPane.setPreferredSize(new Dimension(400, 300));
        
        JOptionPane.showMessageDialog(mainFrame, scrollPane, "Leaderboard", JOptionPane.INFORMATION_MESSAGE);
    }
    
    /**
     * handleOpponentDisconnected - opponent quit or lost connection
     * we win by default and return to lobby
     */
    private void handleOpponentDisconnected(Map<String, Object> msg) {
        // we won by default since opponent disconnected
        player.incrementWins();
        updateStatsDisplay();
        
        inGame = false;
        
        // get parent window for showing dialog
        JFrame parentFrame = gameFrame != null ? gameFrame : (mainFrame != null ? mainFrame : loginFrame);
        
        // bring window to front
        if (parentFrame != null) {
            parentFrame.toFront();
            parentFrame.requestFocus();
        }
        
        // show notification dialog to user
        JOptionPane dialog = new JOptionPane(
            "Your opponent has disconnected from the match.\n\n" +
            "You win by forfeit!\n\n" +
            "You will be returned to the lobby.",
            JOptionPane.INFORMATION_MESSAGE,
            JOptionPane.DEFAULT_OPTION
        );
        
        JDialog notificationDialog = dialog.createDialog(parentFrame, "Opponent Disconnected");
        notificationDialog.setAlwaysOnTop(true); // keep on top
        notificationDialog.toFront();
        notificationDialog.requestFocus();
        notificationDialog.setVisible(true); // show and wait for user to click OK
        
        // after user clicks OK, close game window
        if (gameFrame != null) {
            gameFrame.dispose();
            gameFrame = null;
            gameStatusLabel = null;
        }
        
        // show lobby window
        if (mainFrame != null) {
            mainFrame.setVisible(true);
            mainFrame.toFront();
            mainFrame.requestFocus();
        }
        
        // update status
        showStatus("Status: In Lobby - Opponent disconnected. You win by forfeit!", Color.GREEN);
        
        // refresh player list
        listPlayers();
        
        currentGameId = null;
        currentOpponent = null;
    }
    
    /**
     * sendMessage - sends a message string to the server
     * this is how we communicate with the server
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message); // send message (PrintWriter auto-flushes)
        }
    }
    
    /**
     * listPlayers - asks server for list of online players
     * server will respond with PLAYERS_LIST message
     */
    public void listPlayers() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LIST_PLAYERS");
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * challenge - send challenge to specific player
     * player will receive CHALLENGE message and can accept or reject
     */
    public void challenge(String opponent) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE");
        msg.put("opponent", opponent);
        sendMessage(Protocol.createMessage(msg));
        showStatus("Status: In Lobby - Challenging " + opponent + "...", Color.BLUE);
    }
    
    /**
     * respondToChallenge - reply to someone who challenged us
     * response is either "ACCEPT" or "REJECT"
     */
    public void respondToChallenge(String challenger, String response) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "CHALLENGE_RESPONSE");
        msg.put("challenger", challenger);
        msg.put("response", response);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * makeMove - send a move to the server
     * x and y are 0-2 representing positions on the 3x3 board
     */
    public void makeMove(int x, int y) {
        if (currentGameId == null || !inGame) {
            showStatus("Not in a game", Color.RED);
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
     * requestRematch - ask opponent for another game
     * opponent will receive REMATCH_REQUEST and can accept or decline
     */
    public void requestRematch() {
        if (currentOpponent == null) {
            showStatus("No previous opponent found", Color.RED);
            JOptionPane.showMessageDialog(gameFrame != null ? gameFrame : mainFrame, 
                "No previous opponent found", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        // disable rematch button while request is pending
        if (rematchButton != null) {
            rematchButton.setEnabled(false);
        }
        
        // send rematch request
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_REQUEST");
        msg.put("opponent", currentOpponent);
        sendMessage(Protocol.createMessage(msg));
        showStatus("Requesting rematch with " + currentOpponent + "...", Color.BLUE);
    }
    
    /**
     * respondToRematch - answer rematch request from opponent
     * response is either "ACCEPT" or "REJECT"
     */
    public void respondToRematch(String requester, String response) {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "REMATCH_RESPONSE");
        msg.put("opponent", requester);
        msg.put("response", response);
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * requestLeaderboard - ask server for leaderboard (top players)
     * server will respond with LEADERBOARD message
     */
    public void requestLeaderboard() {
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LEADERBOARD");
        sendMessage(Protocol.createMessage(msg));
    }
    
    /**
     * goBackToLobby - close game window and return to main lobby
     * used when user clicks "Back to Lobby" button after game
     */
    private void goBackToLobby() {
        // close game window
        if (gameFrame != null) {
            gameFrame.dispose();
            gameFrame = null;
        }
        
        // show lobby window
        if (mainFrame != null) {
            mainFrame.setVisible(true);
            mainFrame.toFront();
            mainFrame.requestFocus();
        }
        
        // reset game state
        inGame = false;
        currentGameId = null;
        // keep currentOpponent for potential rematch later
        
        // refresh player list
        listPlayers();
        
        // show lobby status
        showStatus("Status: In Lobby", Color.BLUE);
    }
    
    /**
     * logout - disconnect from server and close application
     * sends LOGOUT message to server first
     */
    public void logout() {
        // tell server we're logging out
        Map<String, Object> msg = new java.util.HashMap<>();
        msg.put("type", "LOGOUT");
        sendMessage(Protocol.createMessage(msg));
        
        // close connection to server
        disconnect();
        
        // close all windows
        if (mainFrame != null) mainFrame.dispose();
        if (gameFrame != null) gameFrame.dispose();
        
        // exit application
        System.exit(0);
    }
    
    /**
     * disconnect - close socket and stop listening for messages
     * used when disconnecting from server
     */
    public void disconnect() {
        // stop the background listener thread
        if (listener != null) {
            listener.stopListening();
        }
        
        // close socket
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }
    
    /**
     * handleServerDisconnection - handle unexpected server disconnection
     * shows error message and shuts down application
     */
    public void handleServerDisconnection(String reason) {
        SwingUtilities.invokeLater(() -> {
            // stop listening first
            if (listener != null) {
                listener.stopListening();
            }
            
            // find which window to show error on
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
            
            // show error dialog
            JOptionPane dialog = new JOptionPane(
                "Connection to server lost.\n\n" + 
                (reason != null ? reason : "The server connection has been closed.") + 
                "\n\nThe application will now close.",
                JOptionPane.ERROR_MESSAGE,
                JOptionPane.DEFAULT_OPTION
            );
            
            JDialog errorDialog = dialog.createDialog(parentFrame, "Server Disconnected");
            errorDialog.setAlwaysOnTop(true); // keep on top
            errorDialog.toFront();
            errorDialog.requestFocus();
            errorDialog.setVisible(true); // show and wait for user to click OK
            
            // close all windows
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
            
            // disconnect
            disconnect();
            
            // exit application
            System.exit(0);
        });
    }
    
    /**
     * showStatus - update status message in appropriate window
     * shows in registration dialog if registering, otherwise in main/game window
     * color parameter controls the text color (red for errors, green for success, etc)
     */
    private void showStatus(String message, Color color) {
        // if registering, show message in registration dialog instead of login window
        if (isRegistering && registrationStatusLabel != null && registrationDialog != null) {
            SwingUtilities.invokeLater(() -> {
                registrationStatusLabel.setText(message);
                registrationStatusLabel.setForeground(color);
            });
            return;
        }
        
        // update main window status label
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
        
        // also update game window status label if it exists
        if (gameStatusLabel != null) {
            gameStatusLabel.setText(message);
            gameStatusLabel.setForeground(color);
        }
    }
    
    /**
     * updateStatsDisplay - refresh player statistics display in main window
     * called after game ends to show updated wins/losses/draws
     */
    private void updateStatsDisplay() {
        if (statsLabel != null) {
            // update stats label with current player stats
            statsLabel.setText(String.format("Wins: %d | Losses: %d | Draws: %d", 
                player.getWins(), player.getLosses(), player.getDraws()));
        }
    }
    
    /**
     * main - entry point for the application
     * creates a new GameClientGUI instance on the EDT (Event Dispatch Thread)
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // use system look and feel (looks native to the OS)
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            // create and start the GUI
            new GameClientGUI();
        });
    }
}

