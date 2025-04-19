package com.github.sahariardev.common;

import com.github.sahariardev.proxy.Server;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private static final ConcurrentHashMap<String, List<Map<String, Object>>> map = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Server> serverMap = new ConcurrentHashMap<>();

    public static final Store INSTANCE = new Store();

    private Store() {
    }

    public List<Map<String, Object>> get(String key) {
        if (!map.containsKey(key)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(map.get(key));
    }

    public List<String> keys() {
        return map.keySet().stream().toList();
    }

    public synchronized void addServer(String key, Server server) {
        map.put(key, new ArrayList<>());
        serverMap.put(key, server);
    }

    public synchronized void put(String key, Map<String, Object> value) {
        UUID uuid = UUID.randomUUID();
        value.put("id", uuid.toString());
        List<Map<String, Object>> list = map.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()));
        list.add(value);
    }

    public Server getServer(String key) {
        return serverMap.get(key);
    }

    public void remove(String key) {
        map.remove(key);
        serverMap.remove(key);
    }
}
