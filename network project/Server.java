/**
 * Server.java - Main server entry point
 * this is simple wrapper that create and start the game server
 */
public class Server {
    public static void main(String[] args) {
        // create new game server instance
        GameServer server = new GameServer();
        // start server so it listen for client connection
        server.start();
    }
}









