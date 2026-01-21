// Students: CSY23102, CSY23052, CSY23031

import java.io.BufferedReader;
import java.io.IOException;

/**
 * serverlistenergui is the swing gui version of serverlistener. runs in its own thread
 * listening for server messages and routing them to the gui client. updates ui when
 * the server connection changes state
 */
public class ServerListenerGUI extends Thread {
    private BufferedReader in;
    private GameClientGUI client;
    private boolean running;
    
    public ServerListenerGUI(BufferedReader in, GameClientGUI client) {
        this.in = in;
        this.client = client;
        this.running = true;
    }
    
    @Override
    public void run() {
        try {
            String message;
            // read messages from server until connection closes
            while (running && (message = in.readLine()) != null) {
                // hand off to gui client for processing
                client.handleServerMessage(message);
            }
            // if we exit the loop normally, the server closed
            if (running) {
                client.handleServerDisconnection("Server closed the connection");
            }
        } catch (IOException e) {
            // connection lost or error reading from socket
            if (running) {
                client.handleServerDisconnection("Connection lost: " + e.getMessage());
            }
        }
    }
    
    public void stopListening() {
        running = false;
    }
}
