package com.github.sahariardev.common;

import com.github.sahariardev.proxy.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoreTest {

    private static final String KEY = "1111:example.com:80";

    @AfterEach
    void cleanup() {
        Store.INSTANCE.remove(KEY);
    }

    @Test
    void addServerRegistersAnEmptyChaosList() {
        Server server = new Server(1111, "example.com", 80, KEY);

        Store.INSTANCE.addServer(KEY, server);

        assertSame(server, Store.INSTANCE.getServer(KEY));
        assertTrue(Store.INSTANCE.get(KEY).isEmpty());
        assertTrue(Store.INSTANCE.keys().contains(KEY));
    }

    @Test
    void putAssignsAnIdAndIsReadableByKey() {
        Store.INSTANCE.addServer(KEY, new Server(1111, "example.com", 80, KEY));
        Map<String, Object> chaos = new HashMap<>();
        chaos.put(Constant.TYPE, "LATENCY");
        chaos.put("latency", 1);

        Store.INSTANCE.put(KEY, chaos);

        assertEquals(1, Store.INSTANCE.get(KEY).size());
        assertNotNull(Store.INSTANCE.get(KEY).get(0).get("id"));
    }

    @Test
    void removeByChaosIdOnlyRemovesTheMatchingEntry() {
        Store.INSTANCE.addServer(KEY, new Server(1111, "example.com", 80, KEY));
        Map<String, Object> first = new HashMap<>();
        first.put(Constant.TYPE, "LATENCY");
        Map<String, Object> second = new HashMap<>();
        second.put(Constant.TYPE, "BANDWIDTH");
        Store.INSTANCE.put(KEY, first);
        Store.INSTANCE.put(KEY, second);

        String firstId = String.valueOf(Store.INSTANCE.get(KEY).get(0).get("id"));
        Store.INSTANCE.remove(KEY, firstId);

        assertEquals(1, Store.INSTANCE.get(KEY).size());
        assertEquals("BANDWIDTH", Store.INSTANCE.get(KEY).get(0).get(Constant.TYPE));
    }

    @Test
    void removeIsSafeForUnknownKeysAndIds() {
        assertDoesNotThrow(() -> Store.INSTANCE.remove("unknown:key:1"));
        assertDoesNotThrow(() -> Store.INSTANCE.remove("unknown:key:1", "missing-id"));
        assertDoesNotThrow(() -> Store.INSTANCE.remove(KEY, null));
    }

    @Test
    void getChaosListReturnsEmptyListForUnknownKey() {
        assertTrue(Store.INSTANCE.getChaosList("does:not:exist").isEmpty());
        assertFalse(Store.INSTANCE.keys().contains("does:not:exist"));
    }

    @Test
    void removeKeyRemovesServerAndChaos() {
        Store.INSTANCE.addServer(KEY, new Server(1111, "example.com", 80, KEY));
        Store.INSTANCE.put(KEY, new HashMap<>(Map.of(Constant.TYPE, "LATENCY")));

        Store.INSTANCE.remove(KEY);

        assertEquals(null, Store.INSTANCE.getServer(KEY));
        assertFalse(Store.INSTANCE.keys().contains(KEY));
    }
}
