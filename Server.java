// Students: CSY23102, CSY23052, CSY23031

/**
 * server entry point that bootstraps the game server instance.
 * this is just a thin wrapper that delegates to gameserver to handle
 * all the networking logic and client management
 */
public class Server {
    public static void main(String[] args) {
        GameServer server = new GameServer();
        server.start();
    }
}
