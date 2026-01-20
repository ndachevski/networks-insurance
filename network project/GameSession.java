import java.util.HashMap;
import java.util.Map;

/**
 * GameSession.java - Manage the actual tic-tac-toe game logic for one game
 * this class hold the game board state, player information, and check for win condition.
 * it also handle moves and determine game result
 */
public class GameSession {
    private String gameId;
    private String player1;
    private String player2;
    // 3x3 board where player1 use X and player2 use O
    private char[][] board;
    private String currentPlayer;
    private String winner;
    private boolean gameOver;
    // count how many move been made so we can check for draw (9 move = full board)
    private int moveCount;
    
    public GameSession(String gameId, String player1, String player2) {
        this.gameId = gameId;
        this.player1 = player1;
        this.player2 = player2;
        // create 3x3 board for tic-tac-toe
        this.board = new char[3][3];
        // player1 go first
        this.currentPlayer = player1;
        this.gameOver = false;
        this.moveCount = 0;
        
        // initialize board with empty space
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                board[i][j] = ' ';
            }
        }
    }
    
    // get game id
    public String getGameId() {
        return gameId;
    }
    
    // get first player
    public String getPlayer1() {
        return player1;
    }
    
    // get second player
    public String getPlayer2() {
        return player2;
    }
    
    // get whose turn it is
    public String getCurrentPlayer() {
        return currentPlayer;
    }
    
    // check if game already finish
    public boolean isGameOver() {
        return gameOver;
    }
    
    // get who win the game (or "DRAW" if it draw)
    public String getWinner() {
        return winner;
    }
    
    /**
     * make a move on the board. place player symbol at x,y position.
     * return true if move valid and accepted, false if move invalid
     */
    public boolean makeMove(String player, int x, int y) {
        // check if game already over
        if (gameOver || !player.equals(currentPlayer)) {
            return false;
        }
        
        // check if coordinate valid and cell empty
        if (x < 0 || x >= 3 || y < 0 || y >= 3 || board[x][y] != ' ') {
            return false;
        }
        
        // place player symbol (X for player1, O for player2)
        board[x][y] = (player.equals(player1)) ? 'X' : 'O';
        moveCount++;
        
        // check if this move win the game
        if (checkWin(x, y)) {
            gameOver = true;
            winner = player;
            return true;
        }
        
        // check if board full (9 move) - mean it draw
        if (moveCount == 9) {
            gameOver = true;
            winner = "DRAW";
            return true;
        }
        
        // game continue so switch to other player turn
        currentPlayer = (currentPlayer.equals(player1)) ? player2 : player1;
        return true;
    }
    
    /**
     * check if move at position x,y result in win. we check row, column, and two diagonal.
     * this method get the symbol placed (X or O) and check if that symbol form line of three
     */
    private boolean checkWin(int x, int y) {
        char symbol = board[x][y];
        
        // check if entire row have all same symbol (all three cell in row x have same symbol)
        if (board[x][0] == symbol && board[x][1] == symbol && board[x][2] == symbol) {
            return true;
        }
        
        // check if entire column have all same symbol (all three cell in column y have same symbol)
        if (board[0][y] == symbol && board[1][y] == symbol && board[2][y] == symbol) {
            return true;
        }
        
        // check diagonal from top-left (0,0) to bottom-right (2,2).
        // this diagonal only matter if x == y because on this diagonal, row index equal column index
        // for example: (0,0), (1,1), (2,2) all have x == y
        if (x == y && board[0][0] == symbol && board[1][1] == symbol && board[2][2] == symbol) {
            return true;
        }
        
        // check diagonal from top-right (0,2) to bottom-left (2,0).
        // on this diagonal, x + y always equal 2. so we can check if x + y == 2
        // for example: (0,2)=0+2, (1,1)=1+1, (2,0)=2+0 all sum to 2
        if (x + y == 2 && board[0][2] == symbol && board[1][1] == symbol && board[2][0] == symbol) {
            return true;
        }
        
        return false;
    }
    
    /**
     * return board as simple string so can print it
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
     * return board as map for sending over network in json format.
     * each cell coordinate become key and symbol become value
     */
    public Map<String, String> getBoardMap() {
        Map<String, String> boardMap = new HashMap<>();
        // go through all cell on board
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                // use "i,j" as key and cell symbol as value
                boardMap.put(i + "," + j, String.valueOf(board[i][j]));
            }
        }
        return boardMap;
    }
    
    /**
     * get what is result for specific player (WIN, LOSS, or DRAW)
     */
    public String getResultFor(String player) {
        // if game not finish yet
        if (!gameOver) {
            return "ONGOING";
        }
        
        // if it draw, everyone get draw
        if (winner.equals("DRAW")) {
            return "DRAW";
        } else if (winner.equals(player)) {
            // if player is winner, they get win
            return "WIN";
        } else {
            // otherwise they lose
            return "LOSS";
        }
    }
}









