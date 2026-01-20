/**
 * Player.java - Store player information on client side
 * this class hold player data like username, stats, authentication status. it simple data holder
 * that help store all player info in one place so client can access it easily
 */
public class Player {
    // player personal information
    private String username;
    private String name;
    private String email;
    private String password;
    
    // player game statistic - track wins, losses, and draws
    private int wins;
    private int losses;
    private int draws;
    
    // whether player currently logged in
    private boolean authenticated;
    
    public Player() {
        this.authenticated = false;
        // start with zero stats for new player
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
    }
    
    // set and get username
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getUsername() {
        return username;
    }
    
    // set and get player full name
    public void setName(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    // set and get player email
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getEmail() {
        return email;
    }
    
    // set and get password
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getPassword() {
        return password;
    }
    
    // set all stats at once
    public void setStats(int wins, int losses, int draws) {
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
    }
    
    // get individual stats
    public int getWins() {
        return wins;
    }
    
    public int getLosses() {
        return losses;
    }
    
    public int getDraws() {
        return draws;
    }
    
    // set authentication status
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    
    // check if player logged in
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    // increment stats when game finish - add one to win count
    public void incrementWins() {
        wins++;
    }
    
    // increment stats when game finish - add one to loss count
    public void incrementLosses() {
        losses++;
    }
    
    // increment stats when game finish - add one to draw count
    public void incrementDraws() {
        draws++;
    }
}





