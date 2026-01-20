import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserManager.java - Handles user registration, authentication, and persistence
 */
public class UserManager {
    private static final String USERS_FILE = "users.txt";
    private Map<String, User> users;
    private Map<String, String> onlineUsers; // username -> sessionId
    
    public UserManager() {
        users = new ConcurrentHashMap<>();
        onlineUsers = new ConcurrentHashMap<>();
        loadUsers();
    }
    
    /**
     * Load users from file
     */
    private void loadUsers() {
        File file = new File(USERS_FILE);
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating users file: " + e.getMessage());
            }
            return;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String username = parts[0];
                    String password = parts[1];
                    
                    // Handle multiple formats for backward compatibility:
                    // - Old format (5 fields): username,password,wins,losses,draws
                    // - Format with nickname (8 fields): username,password,name,email,nickname,wins,losses,draws
                    // - Current format (7 fields): username,password,name,email,wins,losses,draws
                    if (parts.length >= 8) {
                        // Format with nickname (old): username,password,name,email,nickname,wins,losses,draws
                        // Skip nickname (parts[4]) and use name, email
                        String name = parts[2];
                        String email = parts[3];
                        int wins = Integer.parseInt(parts[5]);
                        int losses = Integer.parseInt(parts[6]);
                        int draws = Integer.parseInt(parts[7]);
                        users.put(username, new User(username, password, name, email, wins, losses, draws));
                    } else if (parts.length >= 7) {
                        // Current format: username,password,name,email,wins,losses,draws
                        String name = parts[2];
                        String email = parts[3];
                        int wins = Integer.parseInt(parts[4]);
                        int losses = Integer.parseInt(parts[5]);
                        int draws = Integer.parseInt(parts[6]);
                        users.put(username, new User(username, password, name, email, wins, losses, draws));
                    } else {
                        // Old format: username,password,wins,losses,draws
                    int wins = Integer.parseInt(parts[2]);
                    int losses = Integer.parseInt(parts[3]);
                    int draws = Integer.parseInt(parts[4]);
                    users.put(username, new User(username, password, wins, losses, draws));
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading users: " + e.getMessage());
        }
    }
    
    /**
     * Save users to file with atomic write
     */
    private void saveUsers() {
        File file = new File(USERS_FILE);
        File tempFile = new File(USERS_FILE + ".tmp");
        
        try {
            // Write to temporary file first
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
            for (User user : users.values()) {
                writer.println(user.toFileString());
            }
            }
            
            // Atomic move: replace old file with new one
            if (file.exists()) {
                file.delete();
            }
            tempFile.renameTo(file);
            
            // Set secure file permissions
            setSecureFilePermissions();
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
            // Clean up temp file on error
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * Set secure file permissions (OS-dependent)
     */
    private void setSecureFilePermissions() {
        try {
            File file = new File(USERS_FILE);
            if (!file.exists()) {
                return;
            }
            
            // On Unix-like systems, set permissions to owner read/write only
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // Windows: Use file attributes
                file.setReadable(false, false); // Remove read for others
                file.setWritable(false, false); // Remove write for others
                file.setReadable(true, true);   // Owner can read
                file.setWritable(true, true);   // Owner can write
            } else {
                // Unix/Linux/Mac: Use chmod via Runtime
                Runtime.getRuntime().exec("chmod 600 " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            // Permissions setting is best-effort, don't fail if it doesn't work
            System.err.println("Warning: Could not set file permissions: " + e.getMessage());
        }
    }
    
    /**
     * Register a new user
     */
    public boolean register(String username, String password) {
        return register(username, password, "", "");
    }
    
    /**
     * Register a new user with additional information
     */
    public boolean register(String username, String password, String name, String email) {
        if (users.containsKey(username)) {
            return false; // Username already exists
        }
        
        // Hash the password before storing
        String hashedPassword = SecurityUtils.hashPassword(password);
        users.put(username, new User(username, hashedPassword, name, email, 0, 0, 0));
        saveUsers();
        setSecureFilePermissions();
        return true;
    }
    
    /**
     * Authenticate a user
     */
    public boolean login(String username, String password) {
        User user = users.get(username);
        if (user == null) {
            return false;
        }
        
        String storedPassword = user.getPassword();
        
        // Check if password is hashed or plaintext (for migration)
        if (SecurityUtils.isPlaintext(storedPassword)) {
            // Plaintext password - verify and upgrade to hash
            if (storedPassword.equals(password)) {
                // Upgrade to hashed password
                user.setPassword(SecurityUtils.hashPassword(password));
                saveUsers();
                return true;
            }
            return false;
        } else {
            // Hashed password - verify using hash
            return SecurityUtils.verifyPassword(password, storedPassword);
        }
    }
    
    /**
     * Check if user is online
     */
    public boolean isOnline(String username) {
        return onlineUsers.containsKey(username);
    }
    
    /**
     * Add user to online list
     */
    public void setOnline(String username, String sessionId) {
        onlineUsers.put(username, sessionId);
    }
    
    /**
     * Remove user from online list
     */
    public void setOffline(String username) {
        onlineUsers.remove(username);
    }
    
    /**
     * Get list of online usernames
     */
    public String[] getOnlineUsers() {
        return onlineUsers.keySet().toArray(new String[0]);
    }
    
    /**
     * Get leaderboard (top players by wins)
     */
    public java.util.List<User> getLeaderboard(int limit) {
        java.util.List<User> leaderboard = new java.util.ArrayList<>(users.values());
        leaderboard.sort((a, b) -> {
            // Sort by wins (descending), then by total games
            int winDiff = b.getWins() - a.getWins();
            if (winDiff != 0) return winDiff;
            int totalA = a.getWins() + a.getLosses() + a.getDraws();
            int totalB = b.getWins() + b.getLosses() + b.getDraws();
            return totalB - totalA;
        });
        
        if (limit > 0 && limit < leaderboard.size()) {
            return leaderboard.subList(0, limit);
        }
        return leaderboard;
    }
    
    /**
     * Get user statistics
     */
    public User getUser(String username) {
        return users.get(username);
    }
    
    /**
     * Update user statistics after game
     */
    public void updateStats(String username, String result) {
        User user = users.get(username);
        if (user != null) {
            if ("WIN".equals(result)) {
                user.incrementWins();
            } else if ("LOSS".equals(result)) {
                user.incrementLosses();
            } else if ("DRAW".equals(result)) {
                user.incrementDraws();
            }
            saveUsers();
        }
    }
    
    /**
     * User data class
     */
    public static class User {
        private String username;
        private String password;
        private String name;
        private String email;
        private int wins;
        private int losses;
        private int draws;
        
        public User(String username, String password, int wins, int losses, int draws) {
            this(username, password, "", "", wins, losses, draws);
        }
        
        public User(String username, String password, String name, String email, int wins, int losses, int draws) {
            this.username = username;
            this.password = password;
            this.name = name;
            this.email = email;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
        }
        
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public int getWins() { return wins; }
        public int getLosses() { return losses; }
        public int getDraws() { return draws; }
        
        public void incrementWins() { wins++; }
        public void incrementLosses() { losses++; }
        public void incrementDraws() { draws++; }
        
        public String toFileString() {
            // Format: username,password,name,email,wins,losses,draws
            // For backward compatibility, if name/email are empty, use old format
            if ((name == null || name.isEmpty()) && (email == null || email.isEmpty())) {
            return username + "," + password + "," + wins + "," + losses + "," + draws;
            }
            return username + "," + password + "," + 
                   (name != null ? name : "") + "," + 
                   (email != null ? email : "") + "," + 
                   wins + "," + losses + "," + draws;
        }
    }
}

