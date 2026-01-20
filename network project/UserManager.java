import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserManager.java - manage user account, save and load them from file, track who online
 * this class handle all user related stuff: register new user, login, save data to disk,
 * load data from disk, track who currently online, and keep statistics of win/loss/draw
 */
public class UserManager {
    // file where we save all user data
    private static final String USERS_FILE = "users.txt";
    // in memory store of all user account (username -> user object)
    private Map<String, User> users;
    // track which user currently online (username -> session id)
    private Map<String, String> onlineUsers;
    
    public UserManager() {
        // use concurrent hash map so multiple thread can use it safely
        users = new ConcurrentHashMap<>();
        onlineUsers = new ConcurrentHashMap<>();
        // load user data from file when manager start
        loadUsers();
    }
    
    /**
     * load user from file so we not lose data between server restart.
     * file format: username,password,name,email,wins,losses,draws
     */
    private void loadUsers() {
        File file = new File(USERS_FILE);
        // if file not exist yet, create empty one
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                System.err.println("Error creating users file: " + e.getMessage());
            }
            return;
        }
        
        // read file line by line
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue; // skip empty line
                
                // split line by comma to get field
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String username = parts[0];
                    String password = parts[1];
                    
                    // we need support old file format and new format for backward compatibility
                    // old: username,password,wins,losses,draws
                    // new: username,password,name,email,wins,losses,draws
                    if (parts.length >= 8) {
                        // old format with nickname: username,password,name,email,nickname,wins,losses,draws
                        // skip the nickname (parts[4])
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
                        // old format: username,password,wins,losses,draws
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
     * save all user to file. we write to temp file first, then rename so if something go wrong
     * we not lose old file (atomic operation)
     */
    private void saveUsers() {
        File file = new File(USERS_FILE);
        File tempFile = new File(USERS_FILE + ".tmp");
        
        try {
            // write to temporary file first so old file safe
            try (PrintWriter writer = new PrintWriter(new FileWriter(tempFile))) {
                for (User user : users.values()) {
                    // convert each user to line format and write
                    writer.println(user.toFileString());
                }
            }
            
            // atomic move: delete old file and rename temp file to real file
            // this way if crash happen during save, we don't lose data
            if (file.exists()) {
                file.delete();
            }
            tempFile.renameTo(file);
            
            // set permission on file to protect password
            setSecureFilePermissions();
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
            // clean up temp file if something go wrong
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }
    
    /**
     * set file permission so only owner can read/write (prevent other user on system see password)
     * different system (windows vs linux) need different code
     */
    private void setSecureFilePermissions() {
        try {
            File file = new File(USERS_FILE);
            if (!file.exists()) {
                return;
            }
            
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                // windows: remove read/write for others
                file.setReadable(false, false);
                file.setWritable(false, false);
                // owner can read and write
                file.setReadable(true, true);
                file.setWritable(true, true);
            } else {
                // unix/linux/mac: use chmod command to set 600 (owner read/write only)
                Runtime.getRuntime().exec("chmod 600 " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            // if permission setting fail, not critical, just warn
            System.err.println("Warning: Could not set file permissions: " + e.getMessage());
        }
    }
    
    /**
     * register new user - simple version without name/email
     */
    public boolean register(String username, String password) {
        return register(username, password, "", "");
    }
    
    /**
     * register new user with all information. password get hashed before storage
     */
    public boolean register(String username, String password, String name, String email) {
        // check if username already taken
        if (users.containsKey(username)) {
            return false;
        }
        
        // hash password before store (never store plain password!)
        String hashedPassword = SecurityUtils.hashPassword(password);
        // create new user with 0 win/loss/draw initially
        users.put(username, new User(username, hashedPassword, name, email, 0, 0, 0));
        // save to file so we not lose it if server crash
        saveUsers();
        setSecureFilePermissions();
        return true;
    }
    
    /**
     * authenticate user - check if username exist and password correct
     */
    public boolean login(String username, String password) {
        User user = users.get(username);
        if (user == null) {
            return false; // username not exist
        }
        
        String storedPassword = user.getPassword();
        
        // check if password is old plaintext format or new hash format
        if (SecurityUtils.isPlaintext(storedPassword)) {
            // old plaintext - verify and upgrade to hash
            if (storedPassword.equals(password)) {
                // upgrade to hash format so safer
                user.setPassword(SecurityUtils.hashPassword(password));
                saveUsers();
                return true;
            }
            return false;
        } else {
            // new hash format - use hash verification
            return SecurityUtils.verifyPassword(password, storedPassword);
        }
    }
    
    /**
     * check if user currently online
     */
    public boolean isOnline(String username) {
        return onlineUsers.containsKey(username);
    }
    
    /**
     * mark user as online when they login
     */
    public void setOnline(String username, String sessionId) {
        onlineUsers.put(username, sessionId);
    }
    
    /**
     * mark user as offline when they logout
     */
    public void setOffline(String username) {
        onlineUsers.remove(username);
    }
    
    /**
     * get list of all currently online user (return array of username)
     */
    public String[] getOnlineUsers() {
        return onlineUsers.keySet().toArray(new String[0]);
    }
    
    /**
     * get leaderboard - list of top player sorted by wins, then by total game played
     */
    public java.util.List<User> getLeaderboard(int limit) {
        // create list of all user
        java.util.List<User> leaderboard = new java.util.ArrayList<>(users.values());
        // sort by: first by wins (highest first), then by total game (most game first)
        leaderboard.sort((a, b) -> {
            // compare wins (descending - higher win first)
            int winDiff = b.getWins() - a.getWins();
            if (winDiff != 0) return winDiff;
            // if same wins, compare total games (more game mean more active)
            int totalA = a.getWins() + a.getLosses() + a.getDraws();
            int totalB = b.getWins() + b.getLosses() + b.getDraws();
            return totalB - totalA; // more game first
        });
        
        // return only top N player if limit specified
        if (limit > 0 && limit < leaderboard.size()) {
            return leaderboard.subList(0, limit);
        }
        return leaderboard;
    }
    
    /**
     * get user by username
     */
    public User getUser(String username) {
        return users.get(username);
    }
    
    /**
     * update player stat after game end. result is "WIN", "LOSS", or "DRAW"
     */
    public void updateStats(String username, String result) {
        User user = users.get(username);
        if (user != null) {
            // increment appropriate counter
            if ("WIN".equals(result)) {
                user.incrementWins();
            } else if ("LOSS".equals(result)) {
                user.incrementLosses();
            } else if ("DRAW".equals(result)) {
                user.incrementDraws();
            }
            // save to file so stat not lost
            saveUsers();
        }
    }
    
    /**
     * User - inner class to hold one user account info
     */
    public static class User {
        private String username;
        private String password; // hashed password
        private String name;
        private String email;
        private int wins;
        private int losses;
        private int draws;
        
        // constructor for old format without name/email
        public User(String username, String password, int wins, int losses, int draws) {
            this(username, password, "", "", wins, losses, draws);
        }
        
        // constructor with all field
        public User(String username, String password, String name, String email, int wins, int losses, int draws) {
            this.username = username;
            this.password = password;
            this.name = name;
            this.email = email;
            this.wins = wins;
            this.losses = losses;
            this.draws = draws;
        }
        
        // getter for all field
        public String getUsername() { return username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public int getWins() { return wins; }
        public int getLosses() { return losses; }
        public int getDraws() { return draws; }
        
        // increment stat when game end
        public void incrementWins() { wins++; }
        public void incrementLosses() { losses++; }
        public void incrementDraws() { draws++; }
        
        /**
         * convert user to file format (comma separated). maintain backward compatibility
         * so old format and new format both work
         */
        public String toFileString() {
            // if name and email empty, use old format (backward compatibility)
            if ((name == null || name.isEmpty()) && (email == null || email.isEmpty())) {
                return username + "," + password + "," + wins + "," + losses + "," + draws;
            }
            // else use new format with name and email
            return username + "," + password + "," + 
                   (name != null ? name : "") + "," + 
                   (email != null ? email : "") + "," + 
                   wins + "," + losses + "," + draws;
        }
    }
}

