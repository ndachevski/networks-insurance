// Students: CSY23102, CSY23052, CSY23031

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * usermanager handles all user persistence, authentication, and statistics management.
 * it loads users from a file on startup, maintains online status, and provides
 * leaderboard functionality. authentication uses password hashing via securityutils
 */
public class UserManager {
    private final String usersFilePath;
    // in-memory cache of all users, keyed by username
    private Map<String, User> users;
    // maps online usernames to their session ids for active connection tracking
    private Map<String, String> onlineUsers;
    
    public UserManager() {
        this("users.txt");
    }
    
    /**
     * constructor for testing - allows injection of custom users file path
     */
    public UserManager(String usersFilePath) {
        this.usersFilePath = usersFilePath;
        users = new ConcurrentHashMap<>();
        onlineUsers = new ConcurrentHashMap<>();
        loadUsers();
    }
    
    /**
     * load users from file on startup. handles multiple formats for backward compatibility
     * with migration from plaintext to hashed passwords
     */
    private void loadUsers() {
        File file = new File(usersFilePath);
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
                    
                    // support three format versions:
                    // - old format (5 fields): username,password,wins,losses,draws
                    // - format with nickname (8 fields): username,password,name,email,nickname,wins,losses,draws
                    // - current format (7 fields): username,password,name,email,wins,losses,draws
                    if (parts.length >= 8) {
                        // old format with nickname (skip it and use name, email)
                        String name = parts[2];
                        String email = parts[3];
                        int wins = Integer.parseInt(parts[5]);
                        int losses = Integer.parseInt(parts[6]);
                        int draws = Integer.parseInt(parts[7]);
                        users.put(username, new User(username, password, name, email, wins, losses, draws));
                    } else if (parts.length >= 7) {
                        // current format: username,password,name,email,wins,losses,draws
                        String name = parts[2];
                        String email = parts[3];
                        int wins = Integer.parseInt(parts[4]);
                        int losses = Integer.parseInt(parts[5]);
                        int draws = Integer.parseInt(parts[6]);
                        users.put(username, new User(username, password, name, email, wins, losses, draws));
                    } else {
                        // legacy format: username,password,wins,losses,draws
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
     * save users to file using atomic write pattern to prevent corruption.
     * write to temp file first, then replace the old file. this avoids issues
     * if the process crashes mid-write
     */
    private void saveUsers() {
        File file = new File(usersFilePath);
        File tempFile = new File(usersFilePath + ".tmp");
        
        try {
            // write to temporary file first to avoid partial writes
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
                for (User user : users.values()) {
                    writer.println(user.toFileString());
                }
            }
            
            // atomic move: replace old file with new one
            if (file.exists()) {
                file.delete();
            }
            tempFile.renameTo(file);
            
            // set secure file permissions so only owner can read/write
            setSecureFilePermissions();
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
            // clean up temp file on error
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * set file permissions to make users.txt readable/writable by owner only.
     * this prevents other users on the system from reading the password file
     */
    private void setSecureFilePermissions() {
        try {
            File file = new File(usersFilePath);
            if (!file.exists()) {
                return;
            }
            
            // os-specific permissions: windows file attributes vs unix chmod
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // windows: use file attributes to restrict access
                file.setReadable(false, false);
                file.setWritable(false, false);
                file.setReadable(true, true);
                file.setWritable(true, true);
            } else {
                // unix/linux/mac: use chmod to set permissions
                Runtime.getRuntime().exec("chmod 600 " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            // permissions are best-effort, don't fail if it doesn't work
            System.err.println("Warning: Could not set file permissions: " + e.getMessage());
        }
    }
    
    /**
     * register a new user with hashed password. returns false if username already exists
     */
    public boolean register(String username, String password) {
        return register(username, password, "", "");
    }
    
    /**
     * register a new user with full profile information
     */
    public boolean register(String username, String password, String name, String email) {
        if (users.containsKey(username)) {
            return false;
        }
        
        // hash password before storing - never store plaintext
        String hashedPassword = SecurityUtils.hashPassword(password);
        users.put(username, new User(username, hashedPassword, name, email, 0, 0, 0));
        saveUsers();
        setSecureFilePermissions();
        return true;
    }
    
    /**
     * authenticate a user by username and password. handles migration from plaintext
     * passwords by upgrading them to hashed on successful login. this allows gradual
     * migration from an older plaintext-based system
     */
    public boolean login(String username, String password) {
        User user = users.get(username);
        if (user == null) {
            return false;
        }
        
        String storedPassword = user.getPassword();
        
        // check if password is hashed or plaintext (for migration from old format)
        if (SecurityUtils.isPlaintext(storedPassword)) {
            // plaintext password - verify and upgrade to hash
            if (storedPassword.equals(password)) {
                // upgrade to hashed password on successful login
                user.setPassword(SecurityUtils.hashPassword(password));
                saveUsers();
                return true;
            }
            return false;
        } else {
            // hashed password - verify using hash comparison
            return SecurityUtils.verifyPassword(password, storedPassword);
        }
    }
    
    /**
     * check if a user is currently online
     */
    public boolean isOnline(String username) {
        return onlineUsers.containsKey(username);
    }
    
    /**
     * mark a user as online with their session id
     */
    public void setOnline(String username, String sessionId) {
        onlineUsers.put(username, sessionId);
    }
    
    /**
     * mark a user as offline - clean up when they disconnect
     */
    public void setOffline(String username) {
        onlineUsers.remove(username);
    }
    
    /**
     * get list of currently online usernames
     */
    public String[] getOnlineUsers() {
        return onlineUsers.keySet().toArray(new String[0]);
    }
    
    /**
     * get top N players sorted by wins then by total games played.
     * used for leaderboard display to show the strongest players
     */
    public java.util.List<User> getLeaderboard(int limit) {
        java.util.List<User> leaderboard = new java.util.ArrayList<>(users.values());
        leaderboard.sort((a, b) -> {
            // sort by wins descending, then by total games as tiebreaker to differentiate players with same wins
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
     * retrieve user statistics
     */
    public User getUser(String username) {
        return users.get(username);
    }
    
    /**
     * update user statistics after game completes
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
     * user data class - simple pojo that holds player profile and statistics
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
            // format: username,password,name,email,wins,losses,draws
            // use old format if name/email are empty for backward compatibility
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
