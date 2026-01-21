// Students: CSY23102, CSY23052, CSY23031

import java.util.HashMap;
import java.util.Map;

/**
 * GameSession encapsulates the tic-tac-toe game state and rules logic. It handles
 * board representation, move validation, win conditions, and turn management. one instance
 * of this is created per game on the server side.
 */
public class GameSession {
    private String gameId;
    private String player1;
    private String player2;
    // 3x3 board where ' ' is empty, 'X' is player1, 'O' is player2
    private char[][] board;
    private String currentPlayer; // alternates between player1 and player2
    private String winner; // contains username of winner or "DRAW"
    private boolean gameOver;
    private int moveCount; // tracks total moves to detect draw condition
    
    public GameSession(String gameId, String player1, String player2) {
        this.gameId = gameId;
        this.player1 = player1;
        this.player2 = player2;
        this.board = new char[3][3];
        this.currentPlayer = player1; // player1 always goes first
        this.gameOver = false;
        this.moveCount = 0;
        
        // initialize board with empty cells
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
     * attempt to make a move on the board. validates that the player is the current turn
     * holder and the position is valid and empty. returns false for invalid moves.
     */
    public boolean makeMove(String player, int x, int y) {
        // reject if not this player's turn or game already ended
        if (gameOver || !player.equals(currentPlayer)) {
            return false;
        }
        
        // reject if coordinates are out of bounds or cell is already taken
        if (x < 0 || x >= 3 || y < 0 || y >= 3 || board[x][y] != ' ') {
            return false;
        }
        
        // place symbol based on which player this is
        board[x][y] = (player.equals(player1)) ? 'X' : 'O';
        moveCount++;
        
        // check for win condition immediately after move
        if (checkWin(x, y)) {
            gameOver = true;
            winner = player;
            return true;
        }
        
        // if all 9 cells filled, it's a draw
        if (moveCount == 9) {
            gameOver = true;
            winner = "DRAW";
            return true;
        }
        
        // alternate turn to other player
        currentPlayer = (currentPlayer.equals(player1)) ? player2 : player1;
        return true;
    }
    
    /**
     * check win condition for the position that was just played. we only check row, column,
     * and both diagonals from this position since that's where the winning symbol could be.
     * this optimization avoids checking all nine cells after every move.
     */
    private boolean checkWin(int x, int y) {
        char symbol = board[x][y];
        
        // check entire row
        if (board[x][0] == symbol && board[x][1] == symbol && board[x][2] == symbol) {
            return true;
        }
        
        // check entire column
        if (board[0][y] == symbol && board[1][y] == symbol && board[2][y] == symbol) {
            return true;
        }
        
        // check diagonal if this position is on main diagonal (top-left to bottom-right)
        if (x == y && board[0][0] == symbol && board[1][1] == symbol && board[2][2] == symbol) {
            return true;
        }
        
        // check diagonal if this position is on anti-diagonal (top-right to bottom-left)
        if (x + y == 2 && board[0][2] == symbol && board[1][1] == symbol && board[2][0] == symbol) {
            return true;
        }
        
        return false;
    }
    
    /**
     * serialize board to printable string representation for display
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
     * serialize board to map format for json message. keys are "x,y" coords, values are cell chars
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
     * determine the outcome for a specific player. returns win/loss/draw/ongoing
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
