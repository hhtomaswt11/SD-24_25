package manager;

import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UserManager {
    private final Map<String, User> userMap; // Mapeia nomes de usuários para objetos User
    private final Map<Socket, User> socketUserMap; // Mapeia sockets para objetos User
    private final ReentrantReadWriteLock lock;
    private final ReentrantReadWriteLock.ReadLock readLock;
    private final ReentrantReadWriteLock.WriteLock writeLock;
    private final Condition loginCondition; // Condição para gerenciar logins

    private final int maxLogin = 2; // Número máximo de logins simultâneos permitido
    private int currentLogins = 0; // Número atual de usuários logados

    public UserManager() {
        this.userMap = new HashMap<>();
        this.socketUserMap = new HashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.readLock = lock.readLock();
        this.writeLock = lock.writeLock();
        this.loginCondition = lock.writeLock().newCondition();
    }

    // Método para registrar um novo usuário
    public boolean createUser(User user) {
        writeLock.lock();
        try {
            if (userMap.containsKey(user.getUsername())) {
                return false; // Usuário já existe
            }
            userMap.put(user.getUsername(), user);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    // Método para autenticar um usuário com limite de logins simultâneos
    public boolean authUser(String username, String password, Socket socket) throws InterruptedException {
        writeLock.lock();
        try {
            while (currentLogins >= maxLogin) {
                loginCondition.await(); // Espera enquanto o limite de logins está atingido
            }

            User user = userMap.get(username);
            if (user != null && user.verifyPassword(password) && !user.isLoggedIn()) {
                user.setLoggedIn(true);
                socketUserMap.put(socket, user); // Associa o socket ao usuário
                currentLogins++; // Incrementa o contador de logins
                return true;
            }
            return false; // Falha na autenticação ou usuário já logado
        } finally {
            writeLock.unlock();
        }
    }

    // Método para desconectar um usuário
    public boolean logoutUser(Socket socket) {
        writeLock.lock();
        try {
            User user = socketUserMap.remove(socket); // Remove a associação do socket
            if (user != null && user.isLoggedIn()) {
                user.setLoggedIn(false);
                currentLogins--; // Decrementa o contador de logins
                loginCondition.signal(); // Notifica uma thread em espera para tentar logar
                return true;
            }
            return false; // Usuário não estava logado ou socket não estava associado
        } finally {
            writeLock.unlock();
        }
    }

    // Recupera um usuário pelo nome de usuário
    public User getUser(String username) {
        readLock.lock();
        try {
            return userMap.get(username);
        } finally {
            readLock.unlock();
        }
    }

    // Recupera um usuário pelo socket
    public User getUserBySocket(Socket socket) {
        readLock.lock();
        try {
            return socketUserMap.get(socket);
        } finally {
            readLock.unlock();
        }
    }

    // Recupera o nome de usuário associado a um socket
    public String getUsernameBySocket(Socket socket) {
        readLock.lock();
        try {
            User user = socketUserMap.get(socket);
            return user != null ? user.getUsername() : null;
        } finally {
            readLock.unlock();
        }
    }

    // Remove um usuário (opcional, caso precise deletar contas)
    public boolean removeUser(String username) {
        writeLock.lock();
        try {
            return userMap.remove(username) != null;
        } finally {
            writeLock.unlock();
        }
    }

    // Lista todos os usuários (apenas nomes, por exemplo)
    public String listUsers() {
        readLock.lock();
        try {
            return String.join(", ", userMap.keySet());
        } finally {
            readLock.unlock();
        }
    }

    // Recupera o número atual de logins
    public int getCurrentLogins() {
        readLock.lock();
        try {
            return currentLogins;
        } finally {
            readLock.unlock();
        }
    }
}