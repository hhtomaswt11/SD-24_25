package manager;

import java.io.Serializable;

public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String username;
    private final String password;
    private boolean loggedIn; // Estado de login

    public User(String username, String password) {
        this.username = username;
        this.password = password;
        this.loggedIn = false;
    }

    public String getUsername() {return username;}

    public boolean verifyPassword(String password) {
        return this.password.equals(password);
    }

    public boolean isLoggedIn() {
        return loggedIn;
    }

    public void setLoggedIn(boolean loggedIn) {
        this.loggedIn = loggedIn;
    }
}