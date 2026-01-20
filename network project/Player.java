/**
 * Player.java - Represents local player state
 */
public class Player {
    private String username;
    private String name;
    private String email;
    private String password;
    private int wins;
    private int losses;
    private int draws;
    private boolean authenticated;
    
    public Player() {
        this.authenticated = false;
        this.wins = 0;
        this.losses = 0;
        this.draws = 0;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getName() {
        return name;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getPassword() {
        return password;
    }
    
    public void setStats(int wins, int losses, int draws) {
        this.wins = wins;
        this.losses = losses;
        this.draws = draws;
    }
    
    public int getWins() {
        return wins;
    }
    
    public int getLosses() {
        return losses;
    }
    
    public int getDraws() {
        return draws;
    }
    
    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }
    
    public boolean isAuthenticated() {
        return authenticated;
    }
    
    public void incrementWins() {
        wins++;
    }
    
    public void incrementLosses() {
        losses++;
    }
    
    public void incrementDraws() {
        draws++;
    }
}





