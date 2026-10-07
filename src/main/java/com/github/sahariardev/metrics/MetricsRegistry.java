package com.github.sahariardev.metrics;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-wide registry of {@link ProxyMetrics}, keyed by the same proxy key used by the store.
 */
public final class MetricsRegistry {

    public static final MetricsRegistry INSTANCE = new MetricsRegistry();

    private final ConcurrentHashMap<String, ProxyMetrics> metrics = new ConcurrentHashMap<>();

    private MetricsRegistry() {
    }

    public ProxyMetrics register(String key) {
        return metrics.computeIfAbsent(key, ProxyMetrics::new);
    }

    public ProxyMetrics get(String key) {
        return metrics.get(key);
    }

    public void remove(String key) {
        metrics.remove(key);
    }

    public Map<String, ProxyMetrics> all() {
        return Collections.unmodifiableMap(metrics);
    }
}
