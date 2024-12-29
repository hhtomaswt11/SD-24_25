package common;

import manager.Message;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Demux {
    private final CommonIdent conn;
    private final Lock lock = new ReentrantLock();
    private final Map<Integer, Entry> buf = new HashMap<>();
    private IOException exception = null;
    private volatile boolean running = true; // Para gerenciar a thread de recepção

    public class Entry {
        final Condition cond = lock.newCondition();
        final ArrayDeque<Message> queue = new ArrayDeque<>();
    }

    private Entry get(int id) {
        Entry e = buf.get(id);
        if (e == null) {
            e = new Entry();
            buf.put(id, e);
        }
        return e;
    }

    public Demux(CommonIdent conn) {
        this.conn = conn;
    }

    // Inicia a thread para processar mensagens recebidas
    public void start() {
        new Thread(() -> {
            try {
                while (running) {
                    Message message = conn.receiveMessage(); // Usa o método atualizado do CommonIdent
                    if (message == null) break;

                    lock.lock();
                    try {
                        Entry e = get(message.getType().ordinal()); // Mapeia o tipo para o ID
                        e.queue.add(message);
                        e.cond.signal();
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (IOException e) {
                lock.lock();
                try {
                    exception = e;
                    buf.forEach((k, v) -> v.cond.signalAll());
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    }

    // Enviar uma mensagem genérica
    public void sendMessage(Message message) throws IOException {
        conn.sendMessage(message);
    }

    // Enviar autenticação
    public void sendAuth(int id, String username, String password) throws IOException {
        Message message = new Message(Message.Type.values()[id]);
        message.setPayload(username + ":" + password);
        sendMessage(message);
    }

    // Enviar mensagem PUT
    public void sendMessagePut(int id, String username, String key, String value) throws IOException {
        Message message = new Message(Message.Type.values()[id]);
        message.setKey(key);
        message.setData(value.getBytes());
        message.setPayload(username);
        sendMessage(message);
    }

    // Enviar mensagem GET
    public void sendMessageGet(int id, String username, String key) throws IOException {
        Message message = new Message(Message.Type.values()[id]);
        message.setKey(key);
        message.setPayload(username);
        sendMessage(message);
    }

    // Receber uma mensagem específica pelo tipo
    public Message receive(int id) throws IOException, InterruptedException {
        try {
            lock.lock();
            Entry e = get(id);

            while (e.queue.isEmpty() && exception == null && running) {
                e.cond.await();
            }

            if (!e.queue.isEmpty()) {
                return e.queue.poll();
            }

            if (exception != null) {
                throw exception;
            }

            return null;
        } finally {
            lock.unlock();
        }
    }

    public void sendMessageMultiPut(int id, String username, Map<String, String> pairs) throws IOException {
        Message message = new Message(Message.Type.values()[id]);
        message.setPayload(username);
        message.setData(pairs.toString().getBytes());
        sendMessage(message);
    }

    public void sendMessageMultiGet(int id, String username, Set<String> keys) throws IOException {
        Message message = new Message(Message.Type.values()[id]);
        message.setPayload(username);
        message.setData(String.join(",", keys).getBytes());
        sendMessage(message);
    }

    // Finalizar a conexão e encerrar a thread
    public void close() {
        running = false;
        lock.lock();
        try {
            buf.forEach((k, v) -> v.cond.signalAll()); // Acorda qualquer thread esperando
        } finally {
            lock.unlock();
        }

        conn.close();
    }
}