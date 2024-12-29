package common;

import manager.Message;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;

public class CommonIdent {
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Socket socket;
    private final ReentrantLock lock = new ReentrantLock();
    private final ReentrantLock reslock = new ReentrantLock();


    // Construtor para inicializar a comunicação
    public CommonIdent(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    // Enviar mensagem genérica (compatível com a Message)
    public void sendMessage(Message message) throws IOException {
        lock.lock();
        try {
            this.out.writeInt(message.getType().ordinal());
            this.out.writeUTF(message.getKey() != null ? message.getKey() : "");
            if (message.getData() != null) {
                this.out.writeInt(message.getData().length);
                this.out.write(message.getData());
            } else {
                this.out.writeInt(0);
            }
            this.out.writeUTF(message.getPayload() != null ? message.getPayload() : "");
            this.out.writeBoolean(message.isSuccess());
            this.out.writeUTF(message.getErrorMessage() != null ? message.getErrorMessage() : "");
            this.out.flush();
        } finally {
            lock.unlock();
        }
    }

    // Receber mensagem genérica (compatível com a Message)
    public Message receiveMessage() throws IOException {
        reslock.lock();
        try {
            Message.Type type = Message.Type.values()[this.in.readInt()];
            String key = this.in.readUTF();
            int dataLength = this.in.readInt();
            byte[] data = null;
            if (dataLength > 0) {
                data = new byte[dataLength];
                this.in.readFully(data); // Substituto para readNBytes
            }
            String payload = this.in.readUTF();
            boolean success = this.in.readBoolean();
            String errorMessage = this.in.readUTF();

            Message message = new Message(type);
            message.setKey(key.isEmpty() ? null : key);
            message.setData(data);
            message.setPayload(payload.isEmpty() ? null : payload);
            message.setSuccess(success);
            message.setErrorMessage(errorMessage.isEmpty() ? null : errorMessage);

            return message;
        } finally {
            reslock.unlock();
        }
    }

    // Fechar conexões
    public void cleanup() {
        try {
            this.in.close();
            this.out.close();
            this.socket.close();
        } catch (IOException e) {
            System.err.println("Error closing resources: " + e.getMessage());
        }
    }

    public void close() {
        lock.lock();
        try {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    System.err.println("Error closing input stream: " + e.getMessage());
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    System.err.println("Error closing output stream: " + e.getMessage());
                }
            }
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public Socket getSocket() {
        return socket;
    }
}