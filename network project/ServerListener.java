import java.io.BufferedReader;
import java.io.IOException;

/**
 * ServerListener.java - Thread that listen for incoming message from server on client side
 * this class run in own thread and keep listening to incoming message from server. when message come,
 * it pass message to game client so it can handle it. this way client not block waiting for message
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
            // keep reading message from server until server close or connection break or we stop listening
            while (running && (message = in.readLine()) != null) {
                // pass message to game client so it can handle it
                client.handleServerMessage(message);
            }
            // if we exit the loop without stopping, mean server close connection
            if (running) {
                client.handleServerDisconnection("Server closed the connection");
            }
        } catch (IOException e) {
            // if exception happen and we still running, mean connection lost unexpectedly
            if (running) {
                client.handleServerDisconnection("Connection lost: " + e.getMessage());
            }
        }
    }
    
    // stop listening and exit thread
    public void stopListening() {
        running = false;
    }
}

