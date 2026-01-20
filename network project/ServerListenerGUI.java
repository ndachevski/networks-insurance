import java.io.BufferedReader;
import java.io.IOException;

/**
 * ServerListenerGUI.java - Thread that listen for incoming message from server (gui version of client)
 * this class same as ServerListener but it for gui client. it run in own thread and listen to incoming message.
 * when message come, it pass to gui client so it can handle and update ui with it
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
            // keep reading message from server until server close or connection break or we stop
            while (running && (message = in.readLine()) != null) {
                // pass message to gui client so it can handle and update interface
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









