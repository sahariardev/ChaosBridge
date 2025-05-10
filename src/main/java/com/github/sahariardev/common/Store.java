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

    public List<Map<String, Object>> get(String key) {
        if (!chaosMap.containsKey(key)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(chaosMap.get(key));
    }

    public List<String> keys() {
        return chaosMap.keySet().stream().toList();
    }

    public synchronized void addServer(String key, Server server) {
        chaosMap.put(key, new ArrayList<>());
        serverMap.put(key, server);
    }

    public synchronized void put(String key, Map<String, Object> value) {
        UUID uuid = UUID.randomUUID();
        value.put("id", uuid.toString());
        List<Map<String, Object>> chaosList = chaosMap.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        chaosList.add(value);
    }

    public synchronized void remove(String key, String chaosId) {
        chaosMap.get(key).removeIf(chaos -> chaos.get("id").equals(chaosId));
    }

    public List<Map<String, Object>> getChaosList(String key) {
        return chaosMap.get(key);
    }

    public Server getServer(String key) {
        return serverMap.get(key);
    }

    public void remove(String key) {
        chaosMap.remove(key);
        serverMap.remove(key);
    }
}
