package manager;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    // Tipos de Mensagem
    public enum Type {
        REGISTER, LOGIN, LOGOUT, PUT, GET, MULTIPUT, MULTIGET, GETWHEN, RESPONSE
    }

    private Type type;            // Tipo da mensagem
    private String key;           // Chave (para operações PUT/GET)
    private byte[] data;          // Valor associado à chave
    private String payload;       // Informação adicional (credenciais, notificações, etc.)
    private boolean success;      // Status da operação
    private String errorMessage;  // Mensagem de erro
    public static final int MAX_KEY_LENGTH = 256;
    public static final int MAX_VALUE_SIZE = 1024 * 1024; // 1MB

    // Construtores
    public Message(Type type) {
        this.type = type;
    }

    public Message(Type type, String key, byte[] data) {
        this.type = type;
        this.key = key;
        this.data = data;
    }

    public Message(Type type, String payload) {
        this.type = type;
        this.payload = payload;
    }

    public Message(Type type, boolean success, String errorMessage) {
        this.type = type;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    // Getters e Setters
    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    // Validação de chave e valor
    public static boolean isValidKey(String key) {
        return key != null && !key.trim().isEmpty() && key.length() <= MAX_KEY_LENGTH;
    }

    public static boolean isValidValue(byte[] value) {
        return value != null && value.length > 0 && value.length <= MAX_VALUE_SIZE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Message [type=").append(type);
        if (key != null) sb.append(", key=").append(key);
        if (data != null) sb.append(", data=").append(new String(data));
        if (payload != null) sb.append(", payload=").append(payload);
        sb.append(", success=").append(success);
        if (errorMessage != null) sb.append(", errorMessage=").append(errorMessage);
        sb.append("]");
        return sb.toString();
    }
}