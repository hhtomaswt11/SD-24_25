package client;

import common.*;
import manager.Message;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;

    private Socket socket;
    private Demux demux;
    private Scanner scanner;
    private boolean isAuthenticated = false;
    private String username;

    public Client() {
        scanner = new Scanner(System.in);
    }

    public void start() {
        try {
            connectToServer();
            demux.start();
            showMainMenu();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void connectToServer() throws IOException {
        socket = new Socket(SERVER_HOST, SERVER_PORT);
        demux = new Demux(new CommonIdent(socket));
        System.out.println("Connected to server");
    }

    private void showMainMenu() {
        while (true) {
            try {
                if (!isAuthenticated) {
                    System.out.println("\n1. Register");
                    System.out.println("2. Login");
                    System.out.println("3. Exit");
                    System.out.print("Choose an option: ");
                    String choice = scanner.nextLine();

                    switch (choice) {
                        case "1":
                            register();
                            break;
                        case "2":
                            login();
                            break;
                        case "3":
                            return;
                        default:
                            System.out.println("Invalid option");
                    }
                } else {
                    showOperationMenu();
                }
            } catch (IOException | InterruptedException e) {
                System.err.println("Error: " + e.getMessage());
            }
        }
    }

    private void showOperationMenu() {
        System.out.println("\n1. Put value");
        System.out.println("2. Get value");
        System.out.println("3. MultiPut values");
        System.out.println("4. MultiGet values");
        System.out.println("5. GetWhen");
        System.out.println("6. Logout");
        System.out.print("Choose an option: ");

        String choice = scanner.nextLine();
        try {
            switch (choice) {
                case "1":
                    putValue();
                    break;
                case "2":
                    getValue();
                    break;
                case "3":
                    multiPut();
                    break;
                case "4":
                    multiGet();
                    break;
                case "5":
                    getWhen();
                    break;
                case "6":
                    logout();
                    break;
                default:
                    System.out.println("Invalid option");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Operation failed: " + e.getMessage());
        }
    }

    private void register() throws IOException, InterruptedException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        // Criar mensagem de registro
        Message registerMessage = new Message(Message.Type.REGISTER);
        registerMessage.setPayload(username + ":" + password);

        // Enviar mensagem
        demux.sendMessage(registerMessage);

        // Receber resposta
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response != null) {
            if (response.isSuccess()) {
                System.out.println("Registration successful: " + response.getPayload());
            } else {
                System.out.println("Registration failed: " + response.getErrorMessage());
            }
        } else {
            System.out.println("Failed to register. No response from server.");
        }
    }

    private void login() throws IOException, InterruptedException {
        System.out.print("Enter username: ");
        String username = scanner.nextLine();
        System.out.print("Enter password: ");
        String password = scanner.nextLine();

        // Cria a mensagem de autenticação
        Message loginMessage = new Message(Message.Type.LOGIN);
        loginMessage.setPayload(username + ":" + password);

        // Envia a mensagem de login
        demux.sendMessage(loginMessage);

        // Recebe a resposta
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response != null && response.isSuccess()) {
            this.username = username; // Atualiza o nome de usuário
            isAuthenticated = true;   // Marca o cliente como autenticado
            System.out.println("Login successful: " + response.getPayload());
        } else {
            System.out.println("Login failed: " + (response != null ? response.getPayload() : "No response from server"));
        }
    }

    private void getWhen() throws IOException, InterruptedException {
        System.out.print("Enter key to retrieve: ");
        String key = scanner.nextLine();
        System.out.print("Enter conditional key: ");
        String keyCond = scanner.nextLine();
        System.out.print("Enter conditional value: ");
        String valueCond = scanner.nextLine();

        // Cria a mensagem GETWHEN
        Message getWhenMessage = new Message(Message.Type.GETWHEN);
        getWhenMessage.setKey(key);
        getWhenMessage.setPayload(keyCond);
        getWhenMessage.setData(valueCond.getBytes());

        // Envia a mensagem para o servidor
        demux.sendMessage(getWhenMessage);

        // Recebe a resposta do servidor
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response != null && response.isSuccess()) {
            System.out.println("Condition met. Retrieved value: " + new String(response.getData()));
        } else {
            System.out.println("Failed to retrieve value: " + (response != null ? response.getPayload() : "No response from server"));
        }
    }

    private void putValue() throws IOException, InterruptedException {
        System.out.print("Enter key: ");
        String key = scanner.nextLine();
        System.out.print("Enter value: ");
        String value = scanner.nextLine();

        // Cria uma mensagem para o PUT
        Message putMessage = new Message(Message.Type.PUT);
        putMessage.setKey(key);
        putMessage.setData(value.getBytes());
        putMessage.setPayload(username); // Inclui o nome do usuário

        // Envia a mensagem para o servidor
        demux.sendMessage(putMessage);

        // Recebe a resposta do servidor
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response.isSuccess()) {
            System.out.println("Value stored successfully: " + response.getPayload());
        } else {
            System.out.println("Failed to store value: " + response.getPayload());
        }
    }

    private void getValue() throws IOException, InterruptedException {
        System.out.print("Enter key: ");
        String key = scanner.nextLine();

        // Cria a mensagem para GET
        Message getMessage = new Message(Message.Type.GET);
        getMessage.setKey(key);
        getMessage.setPayload(username); // Envia o nome do usuário no payload

        // Envia a mensagem
        demux.sendMessage(getMessage);

        // Recebe a resposta do servidor
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response != null) {
            if (response.isSuccess()) {
                System.out.println("Value: " + new String(response.getData())); // Exibe o valor
            } else {
                System.out.println("Failed to retrieve value: " + response.getPayload()); // Exibe a mensagem de erro
            }
        } else {
            System.out.println("No response received from server.");
        }
    }

    private void multiPut() throws IOException, InterruptedException {
        System.out.println("Enter the number of key-value pairs:");
        int n = Integer.parseInt(scanner.nextLine());

        Map<String, String> pairs = new HashMap<>();
        for (int i = 0; i < n; i++) {
            System.out.println("Key " + (i + 1) + ":");
            String key = scanner.nextLine();
            System.out.println("Value " + (i + 1) + ":");
            String value = scanner.nextLine();
            pairs.put(key, value);
        }

        // Serializa os pares no formato "key1=value1,key2=value2"
        String serializedPairs = pairs.entrySet()
                .stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(","));

        // Cria a mensagem MULTIPUT
        Message message = new Message(Message.Type.MULTIPUT);
        message.setPayload(username); // Nome do usuário
        message.setData(serializedPairs.getBytes()); // Serializa os pares como bytes

        // Envia a mensagem para o servidor
        demux.sendMessage(message);

        // Recebe a resposta do servidor
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response != null) {
            if (response.isSuccess()) {
                System.out.println("MultiPut successful: " + response.getPayload());
            } else {
                System.out.println("MultiPut failed: " + response.getPayload());
            }
        } else {
            System.out.println("No response received from server.");
        }
    }

    private void multiGet() throws IOException, InterruptedException {
        System.out.println("Enter the number of keys to retrieve:");
        int n = Integer.parseInt(scanner.nextLine());

        Set<String> keys = new HashSet<>();
        for (int i = 0; i < n; i++) {
            System.out.println("Key " + (i + 1) + ":");
            keys.add(scanner.nextLine());
        }

        // Serializa as chaves no formato "key1,key2,key3,..."
        String serializedKeys = String.join(",", keys);

        // Cria a mensagem MULTIGET
        Message message = new Message(Message.Type.MULTIGET);
        message.setPayload(username); // Nome do usuário
        message.setData(serializedKeys.getBytes()); // Converte o conjunto de chaves para bytes

        // Envia a mensagem para o servidor
        demux.sendMessage(message);

        // Recebe a resposta do servidor
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response != null) {
            if (response.isSuccess()) {
                System.out.println("Values retrieved: " + response.getPayload());
            } else {
                System.out.println("MultiGet failed: " + response.getPayload());
            }
        } else {
            System.out.println("No response received from server.");
        }
    }
    private void logout() throws IOException, InterruptedException {
        if (!isAuthenticated) {
            System.out.println("You are not logged in!");
            return;
        }

        // Cria e envia a mensagem de logout
        Message logoutMessage = new Message(Message.Type.LOGOUT);
        logoutMessage.setPayload(username);
        demux.sendMessage(logoutMessage);

        // Recebe a resposta do servidor
        Message response = demux.receive(Message.Type.RESPONSE.ordinal());
        if (response != null && response.isSuccess()) {
            System.out.println(response.getPayload()); // Exibe a mensagem de sucesso
            isAuthenticated = false;                  // Marca o cliente como deslogado
        } else {
            System.out.println("Logout failed: " + (response != null ? response.getPayload() : "No response from server"));
        }
    }
    private void cleanup() {
        try {
            if (scanner != null) scanner.close();
            if (demux != null) {
                try {
                    demux.close();
                } catch (Exception e) {
                    System.err.println("Error closing Demux: " + e.getMessage());
                }
            }
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("IO Error during cleanup: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Client().start();
    }
}