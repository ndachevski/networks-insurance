// Students: CSY23102, CSY23052, CSY23031

import java.io.BufferedReader;
import java.io.IOException;

/**
 * serverlistener runs in its own thread and continuously reads messages from the server socket.
 * when a message arrives, it hands off to the client for processing. if the connection closes
 * or an error occurs, it notifies the client so it can handle disconnection gracefully
 */
public class ServerListener extends Thread {
    private BufferedReader in;
    private GameClient client;
    private boolean running;
    
    public ServerListener(BufferedReader in, GameClient client) {
        this.in = in;
        this.client = client;
        this.running = true;
    }
    
    @Override
    public void run() {
        try {
            String message;
            // continuously read messages from server until connection closes
            while (running && (message = in.readLine()) != null) {
                // hand off to client for processing
                client.handleServerMessage(message);
            }
            // if we exit the loop normally, the server closed the connection
            if (running) {
                client.handleServerDisconnection("Server closed the connection");
            }
        } catch (IOException e) {
            // io error means connection was lost unexpectedly
            if (running) {
                client.handleServerDisconnection("Connection lost: " + e.getMessage());
            }
        }
    }
    
    public void stopListening() {
        running = false;
    }
}
