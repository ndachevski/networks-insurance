import java.util.HashMap;
import java.util.Map;

/**
 * GameSession.java - Manages Tic-Tac-Toe game logic
 */
public class GameSession {
    private String gameId;
    private String player1;
    private String player2;
    private char[][] board;
    private String currentPlayer;
    private String winner;
    private boolean gameOver;
    private int moveCount;
    
    public GameSession(String gameId, String player1, String player2) {
        this.gameId = gameId;
        this.player1 = player1;
        this.player2 = player2;
        this.board = new char[3][3];
        this.currentPlayer = player1;
        this.gameOver = false;
        this.moveCount = 0;
        
        // Initialize empty board
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = ' ';
            }
        }
    }
    
    public String getGameId() {
        return gameId;
    }
    
    public String getPlayer1() {
        return player1;
    }
    
    public String getPlayer2() {
        return player2;
    }
    
    public String getCurrentPlayer() {
        return currentPlayer;
    }
    
    public boolean isGameOver() {
        return gameOver;
    }
    
    public String getWinner() {
        return winner;
    }
    
    /**
     * Make a move on the board
     */
    public boolean makeMove(String player, int x, int y) {
        if (gameOver || !player.equals(currentPlayer)) {
            return false;
        }
        
        if (x < 0 || x >= 3 || y < 0 || y >= 3 || board[x][y] != ' ') {
            return false;
        }
        
        board[x][y] = (player.equals(player1)) ? 'X' : 'O';
        moveCount++;
        
        // Check for win
        if (checkWin(x, y)) {
            gameOver = true;
            winner = player;
            return true;
        }
        
        // Check for draw
        if (moveCount == 9) {
            gameOver = true;
            winner = "DRAW";
            return true;
        }
        
        // Switch player
        currentPlayer = (currentPlayer.equals(player1)) ? player2 : player1;
        return true;
    }
    
    /**
     * Check if the last move resulted in a win
     */
    private boolean checkWin(int x, int y) {
        char symbol = board[x][y];
        
        // Check row
        if (board[x][0] == symbol && board[x][1] == symbol && board[x][2] == symbol) {
            return true;
        }
        
        // Check column
        if (board[0][y] == symbol && board[1][y] == symbol && board[2][y] == symbol) {
            return true;
        }
        
        // Check diagonal (top-left to bottom-right)
        if (x == y && board[0][0] == symbol && board[1][1] == symbol && board[2][2] == symbol) {
            return true;
        }
        
        // Check diagonal (top-right to bottom-left)
        if (x + y == 2 && board[0][2] == symbol && board[1][1] == symbol && board[2][0] == symbol) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Get board state as string representation
     */
    public String getBoardString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                sb.append(board[i][j]);
                if (j < 2) sb.append("|");
            }
            if (i < 2) sb.append("\n-+-+-\n");
        }
        return sb.toString();
    }
    
    /**
     * Get board state as map for JSON
     */
    public Map<String, String> getBoardMap() {
        Map<String, String> boardMap = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                boardMap.put(i + "," + j, String.valueOf(board[i][j]));
            }
        }
        return boardMap;
    }
    
    /**
     * Get result for a specific player
     */
    public String getResultFor(String player) {
        if (!gameOver) {
            return "ONGOING";
        }
        
        if (winner.equals("DRAW")) {
            return "DRAW";
        } else if (winner.equals(player)) {
            return "WIN";
        } else {
            return "LOSS";
        }
    }
}









