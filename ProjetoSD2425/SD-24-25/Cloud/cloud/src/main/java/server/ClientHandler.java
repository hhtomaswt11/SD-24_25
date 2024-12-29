package server;

import common.*;
import manager.MapAccess;
import manager.Message;
import manager.User;
import manager.UserManager;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final UserManager users;
    private final MapAccess mapAccess;

    public ClientHandler(Socket clientSocket, UserManager users, MapAccess mapAccess) {
        this.clientSocket = clientSocket;
        this.users = users;
        this.mapAccess = mapAccess;
    }

    @Override
    public void run() {
        CommonIdent commonIdent = null;
        try {
            commonIdent = new CommonIdent(clientSocket);
            while (true) {
                Message message = commonIdent.receiveMessage();
                handleMessage(message, commonIdent);
            }
        } catch (Exception e) {
            System.err.println("Client handler error: " + e.getMessage());
        } finally {
            if (commonIdent != null) {
                try {
                    handleLogout(commonIdent); // Garante logout ao desconectar
                } catch (IOException ignored) {}
                commonIdent.cleanup();
            }
        }
    }

    private void handleMessage(Message message, CommonIdent commonIdent) throws IOException {
        String username = users.getUsernameBySocket(commonIdent.getSocket()); // Recupera o user pelo socket

        switch (message.getType()) {
            case REGISTER:
                handleRegister(message, commonIdent);
                break;
            case LOGIN:
                handleLogin(message, commonIdent);
                break;
            case LOGOUT:
                handleLogout(commonIdent);
                break;
            case PUT:
                handlePut(message, commonIdent, username);
                break;
            case GET:
                handleGet(message, commonIdent, username);
                break;
            case MULTIPUT:
                handleMultiPut(message, commonIdent, username);
                break;
            case MULTIGET:
                handleMultiGet(message, commonIdent, username);
                break;
            case GETWHEN:
                handleGetWhen(message, commonIdent, username);
                break;
            default:
                logAction("INVALID MESSAGE TYPE", username, "FAILED", null);
                commonIdent.sendMessage(new Message(Message.Type.RESPONSE, false, "Invalid message type"));
        }
    }

    private void handleRegister(Message message, CommonIdent commonIdent) throws IOException {
        String[] credentials = message.getPayload().split(":");
        if (credentials.length != 2) {
            sendResponse(commonIdent, false, "Invalid registration format");
            logAction("REGISTER", "UNKNOWN", "FAILED", "Invalid registration format");
            return;
        }

        String username = credentials[0];
        String password = credentials[1];
        boolean userCreated = users.createUser(new User(username, password));

        if (userCreated) {
            sendResponse(commonIdent, true, "Registration successful");
            logAction("REGISTER", username, "SUCCESS", "User registered");
        } else {
            sendResponse(commonIdent, false, "Username already exists");
            logAction("REGISTER", username, "FAILED", "Username already exists");
        }
    }

    private void handleLogin(Message message, CommonIdent commonIdent) throws IOException {
        String[] credentials = message.getPayload().split(":");
        if (credentials.length != 2) {
            sendResponse(commonIdent, false, "Invalid login format");
            logAction("LOGIN", "UNKNOWN", "FAILED", "Invalid login format");
            return;
        }

        String username = credentials[0];
        String password = credentials[1];

        boolean authenticated = false;
        try {
            authenticated = users.authUser(username, password, commonIdent.getSocket());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restabelece o estado de interrupção da thread
            sendResponse(commonIdent, false, "Login interrupted");
            logAction("LOGIN", username, "FAILED", "Login interrupted");
            return;
        }

        if (authenticated) {
            sendResponse(commonIdent, true, "Login successful");
            logAction("LOGIN", username, "SUCCESS", "User logged in");
        } else {
            sendResponse(commonIdent, false, "Authentication failed");
            logAction("LOGIN", username, "FAILED", "Invalid credentials or already logged in");
        }
    }

    private void handleLogout(CommonIdent commonIdent) throws IOException {
        boolean loggedOut = users.logoutUser(commonIdent.getSocket());
        String username = users.getUsernameBySocket(commonIdent.getSocket());

        if (loggedOut) {
            sendResponse(commonIdent, true, "Logged out successfully");
            logAction("LOGOUT", username, "SUCCESS", "User logged out");
        } else {
            sendResponse(commonIdent, false, "User not logged in");
            logAction("LOGOUT", username != null ? username : "UNKNOWN", "FAILED", "User not logged in");
        }
    }

    private void handlePut(Message message, CommonIdent commonIdent, String username) throws IOException {
        if (username == null) {
            sendResponse(commonIdent, false, "User not logged in");
            logAction("PUT", "UNKNOWN", "FAILED", "User not logged in");
            return;
        }

        if (!Message.isValidKey(message.getKey())) {
            sendResponse(commonIdent, false, "Invalid key format");
            logAction("PUT", username, "FAILED", "Invalid key format");
            return;
        }

        if (!Message.isValidValue(message.getData())) {
            sendResponse(commonIdent, false, "Invalid value format or size");
            logAction("PUT", username, "FAILED", "Invalid value format or size");
            return;
        }

        mapAccess.put(message.getKey(), message.getData());
        sendResponse(commonIdent, true, "Value stored successfully");
        logAction("PUT", username, "SUCCESS", "Key stored: " + message.getKey());
    }

    private void handleGet(Message message, CommonIdent commonIdent, String username) throws IOException {
        if (username == null) {
            sendResponse(commonIdent, false, "User not logged in");
            logAction("GET", "UNKNOWN", "FAILED", "User not logged in");
            return;
        }

        if (!Message.isValidKey(message.getKey())) {
            sendResponse(commonIdent, false, "Invalid key format");
            logAction("GET", username, "FAILED", "Invalid key format");
            return;
        }

        byte[] value = mapAccess.get(message.getKey());
        if (value != null) {
            Message response = new Message(Message.Type.RESPONSE);
            response.setData(value);
            response.setSuccess(true);
            commonIdent.sendMessage(response);

            logAction("GET", username, "SUCCESS", "Key retrieved: " + message.getKey());
        } else {
            sendResponse(commonIdent, false, "Key not found");
            logAction("GET", username, "FAILED", "Key not found: " + message.getKey());
        }
    }

    private void handleMultiPut(Message message, CommonIdent commonIdent, String username) throws IOException {
        if (username == null) {
            sendResponse(commonIdent, false, "User not logged in");
            logAction("MULTIPUT", "UNKNOWN", "FAILED", "User not logged in");
            return;
        }

        if (message.getData() == null || message.getData().length == 0) {
            sendResponse(commonIdent, false, "Invalid data for MultiPut");
            logAction("MULTIPUT", username, "FAILED", "Invalid data");
            return;
        }

        String data = new String(message.getData());
        Map<String, String> pairs = Arrays.stream(data.split(","))
                .map(pair -> pair.split("="))
                .collect(Collectors.toMap(a -> a[0].trim(), a -> a[1].trim()));

        for (Map.Entry<String, String> entry : pairs.entrySet()) {
            mapAccess.put(entry.getKey(), entry.getValue().getBytes());
        }

        String storedKeys = String.join(", ", pairs.keySet());
        sendResponse(commonIdent, true, "MultiPut completed");
        logAction("MULTIPUT", username, "SUCCESS", "Stored " + pairs.size() + " pairs (" + storedKeys + ")");
    }

    
    private void handleMultiGet(Message message, CommonIdent commonIdent, String username) throws IOException {
        String data = new String(message.getData());
        Set<String> keys = new HashSet<>(Arrays.asList(data.split(",")));
        
         // Busca os valores para as chaves
        Map<String, byte[]> values = mapAccess.multiGet(keys);
    
       
        if (!keys.isEmpty()) {
            String responsePayload = keys.stream()
                    .map(key -> "(" + key + "," + (values.containsKey(key) && values.get(key) != null ? new String(values.get(key)) : "null") + ")")
                    .collect(Collectors.joining(", "));
    
            sendResponse(commonIdent, true, responsePayload);
            logAction("MULTIGET", username, "SUCCESS", "Retrieved " + values.size() + " values");
        } else {
            sendResponse(commonIdent, false, "No matching keys found");
            logAction("MULTIGET", username, "FAILED", "No matching keys");
        }
    }

    private void handleGetWhen(Message message, CommonIdent commonIdent, String username) throws IOException {
        if (username == null) {
            sendResponse(commonIdent, false, "User not logged in");
            logAction("GETWHEN", "UNKNOWN", "FAILED", "User not logged in");
            return;
        }

        String key = message.getKey();
        String keyCond = message.getPayload(); // Usa o payload para passar o keyCond
        byte[] valueCond = message.getData();  // Usa o data para passar o valueCond

        if (!Message.isValidKey(key) || !Message.isValidKey(keyCond) || !Message.isValidValue(valueCond)) {
            sendResponse(commonIdent, false, "Invalid key or value format");
            logAction("GETWHEN", username, "FAILED", "Invalid key or value format");
            return;
        }

        byte[] result;
        try {
            result = mapAccess.getWhen(key, keyCond, valueCond);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Marca a thread como interrompida
            sendResponse(commonIdent, false, "Operation interrupted");
            logAction("GETWHEN", username, "FAILED", "Operation interrupted");
            return;
        }

        if (result != null) {
            Message response = new Message(Message.Type.RESPONSE);
            response.setSuccess(true);
            response.setData(result);
            commonIdent.sendMessage(response);
            logAction("GETWHEN", username, "SUCCESS", "Key retrieved: " + key);
            
        } else {
            Message response = new Message(Message.Type.RESPONSE);
            response.setSuccess(true);
            response.setData("null".getBytes());
            commonIdent.sendMessage(response);
            logAction("GETWHEN", username, "SUCCESS", "Key retrieved: " + key);           
        }
    }

    private void sendResponse(CommonIdent commonIdent, boolean success, String message) throws IOException {
        Message response = new Message(Message.Type.RESPONSE);
        response.setSuccess(success);
        response.setPayload(message);
        commonIdent.sendMessage(response);
    }

    private void logAction(String action, String username, String status, String details) {
        System.out.printf("SOCKET [%s] - USER [%s] - ACTION [%s - %s] - DETAILS [%s]%n",
                clientSocket.getRemoteSocketAddress(), username, action, status, details);
    }
}
