package server;

import manager.MapAccess;
import manager.UserManager;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public class Server {
    private static final int PORT = 8080;
    private final UserManager users = new UserManager(); // Agora o UserManager gerencia o socketUserMap
    private final MapAccess mapAccess = new MapAccess();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                threadPool.execute(new ClientHandler(clientSocket, users, mapAccess));
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Server().start();
    }
}