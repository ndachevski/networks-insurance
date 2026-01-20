import java.io.BufferedReader;
import java.io.IOException;

/**
 * ServerListener.java - Thread that listens for incoming server messages
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
            while (running && (message = in.readLine()) != null) {
                client.handleServerMessage(message);
            }
            // If we exit the loop, the server closed the connection
            if (running) {
                client.handleServerDisconnection("Server closed the connection");
            }
        } catch (IOException e) {
            if (running) {
                client.handleServerDisconnection("Connection lost: " + e.getMessage());
            }
        }
    }
    
    public void stopListening() {
        running = false;
    }
}

