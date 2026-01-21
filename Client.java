// Students: CSY23102, CSY23052, CSY23031

/**
 * client is a simple wrapper that delegates to gameclient.
 * provides a clean entry point for the command-line version
 */
public class Client {
    public static void main(String[] args) {
        // forward to gameclient main which handles all the logic
        GameClient.main(args);
    }
}

