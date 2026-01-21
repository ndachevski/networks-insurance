// Students: CSY23102, CSY23052, CSY23031

/**
 * clientgui is a wrapper that delegates to gameclientgui.
 * provides clean entry point for the swing gui version
 */
public class ClientGUI {
    public static void main(String[] args) {
        // forward to gameclientgui main which sets up the swing ui
        GameClientGUI.main(args);
    }
}
