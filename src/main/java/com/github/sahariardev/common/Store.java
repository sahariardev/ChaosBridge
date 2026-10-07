package com.github.sahariardev.common;

import com.github.sahariardev.proxy.Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> chaosMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Server> serverMap = new ConcurrentHashMap<>();

    public static final Store INSTANCE = new Store();

    private Store() {
    }

    /**
     * Returns an immutable point-in-time snapshot of the chaos profiles for a key. A snapshot (rather
     * than a live view) lets the proxy read the active profiles on every chunk without risking a
     * {@link ConcurrentModificationException} when chaos is added or removed mid-transfer.
     */
    public List<Map<String, Object>> get(String key) {
        List<Map<String, Object>> list = chaosMap.get(key);
        if (list == null) {
            return Collections.emptyList();
        }
        synchronized (list) {
            return List.copyOf(list);
        }
    }

    public List<String> keys() {
        return chaosMap.keySet().stream().toList();
    }

    public synchronized void addServer(String key, Server server) {
        chaosMap.put(key, new ArrayList<>());
        serverMap.put(key, server);
    }

    public void put(String key, Map<String, Object> value) {
        value.put("id", UUID.randomUUID().toString());
        List<Map<String, Object>> chaosList = chaosMap.computeIfAbsent(key, k -> new ArrayList<>());
        synchronized (chaosList) {
            chaosList.add(value);
        }
    }

    public void remove(String key, String chaosId) {
        List<Map<String, Object>> chaosList = chaosMap.get(key);
        if (chaosList == null || chaosId == null) {
            return;
        }
        synchronized (chaosList) {
            chaosList.removeIf(chaos -> chaosId.equals(chaos.get("id")));
        }
    }

    public List<Map<String, Object>> getChaosList(String key) {
        return get(key);
    }

    public Server getServer(String key) {
        return serverMap.get(key);
    }

    public void remove(String key) {
        chaosMap.remove(key);
        serverMap.remove(key);
    }
}
