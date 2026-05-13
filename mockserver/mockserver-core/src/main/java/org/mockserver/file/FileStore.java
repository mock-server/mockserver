package org.mockserver.file;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileStore {
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();

    public void store(String name, byte[] content) {
        files.put(name, content);
    }

    public byte[] retrieve(String name) {
        return files.get(name);
    }

    public boolean exists(String name) {
        return files.containsKey(name);
    }

    public boolean delete(String name) {
        return files.remove(name) != null;
    }

    public Set<String> listFiles() {
        return files.keySet();
    }

    public void reset() {
        files.clear();
    }

    public int size() {
        return files.size();
    }
}
