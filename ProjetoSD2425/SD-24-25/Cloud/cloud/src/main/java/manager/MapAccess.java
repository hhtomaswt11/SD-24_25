package manager;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MapAccess {
    private final Map<String, byte[]> mapKeyValue;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock rl = lock.readLock();
    private final ReentrantReadWriteLock.WriteLock wl = lock.writeLock();
    private final Map<String, Condition> conditionMap;

    public MapAccess() {
        this.mapKeyValue = new HashMap<>();
        this.conditionMap = new HashMap<>();
    }

    public void put(String key, byte[] value) {
        wl.lock();
        try {
            if (mapKeyValue.containsKey(key)) {
                System.out.println("Key already exists. Updating value...");
            } else {
                System.out.println("Unknown key. Creating new entry in the map...");
            }
            mapKeyValue.put(key, value);

            // Alerta a condição para a chave específica caso ela exista
            Condition condition = conditionMap.get(key);
            if (condition != null) {
                //  System.out.println("Vai alertar as threads que atualizacao na chave " + key + " foi feita.");
                condition.signalAll();
            }
        } finally {
            wl.unlock();
        }
    }

    public void multiPut(Map<String, byte[]> pairs) {
        wl.lock();
        try {
            for (Map.Entry<String, byte[]> entry : pairs.entrySet()) {
                String key =  entry.getKey();
                byte[] value = entry.getValue();
                if (mapKeyValue.containsKey(key)) {
                    System.out.println("Key already exists. Updating value...");
                } else {
                    System.out.println("Unknown key. Creating new entry in the map...");
                }
                mapKeyValue.put(key, value);

                // Alerta a condição para a chave específica caso ela exista
                Condition condition = conditionMap.get(key);
                if (condition != null) {
                    //  System.out.println("Vai alertar as threads que atualizacao na chave " + key + " foi feita.");
                    condition.signalAll();
                }
            }
        } finally {
            wl.unlock();
        }
    }

    public byte[] get(String key) {
        rl.lock();
        try {
            if (mapKeyValue.containsKey(key)) {
                System.out.println("Key found. Returning value...");
                return mapKeyValue.get(key);
            } else {
                System.out.println("Key not found. Returning null...");
                return "null".getBytes();
            }
        } finally {
            rl.unlock();
        }
    }

    public Map<String, byte[]> multiGet(Set<String> keys) {
        rl.lock();
        try {
            Map<String, byte[]> result = new HashMap<>();
            for (String key : keys) {
                if (mapKeyValue.containsKey(key)) {
                    result.put(key, mapKeyValue.get(key));
                    System.out.println("Key found: " + key + ". Adding to the result map.");
                } else {
                    System.out.println("Key not found: " + key + ". Skipping...");
                }
            }
            return result;
        } finally {
            rl.unlock();
        }
    }

    public byte[] getWhen(String key, String keyCond, byte[] valueCond) throws InterruptedException {
        wl.lock();
        try {
            // Create a condition for keyCond if it does not exist
            conditionMap.putIfAbsent(keyCond, wl.newCondition());
            // System.out.println("Add " + keyCond  + " to conditionMap");

            Condition condition = conditionMap.get(keyCond);

            // Espera até que a condition seja satisfeita
            while (!mapKeyValue.containsKey(keyCond) || !Arrays.equals(mapKeyValue.get(keyCond), valueCond)) {
                condition.await();
            }
            byte[] value = mapKeyValue.get(keyCond);
            String valueStr = new String(value);
            String strValueCond = new String (valueCond);

            System.out.println("Now " + strValueCond  + " equals to " + valueStr );

            return mapKeyValue.get(key); // value associated with 'key'
        } finally {
            wl.unlock();
        }
    }

    public void clear() {
        wl.lock();
        try {
            mapKeyValue.clear();
            conditionMap.clear();
        } finally {
            wl.unlock();
        }
    }

    public Set<String> keySet() {
        rl.lock();
        try {
            return new HashSet<>(mapKeyValue.keySet());
        } finally {
            rl.unlock();
        }
    }

    public void remove(String key) {
        wl.lock();
        try {
            if (mapKeyValue.containsKey(key)) {
                mapKeyValue.remove(key);
            }
            conditionMap.remove(key); // remove the condition associated with the 'key'
        } finally {
            wl.unlock();
        }
    }


}