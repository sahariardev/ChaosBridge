package com.github.sahariardev.metrics;

import java.io.OutputStream;
import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free counters for a single proxy. All values are cumulative for the lifetime of the proxy
 * unless stated otherwise. Uses {@link LongAdder} so concurrent virtual-thread connections do not
 * contend on a single atomic.
 */
public class ProxyMetrics {

    private static final ProxyMetrics NOOP = new ProxyMetrics("__noop__");

    private final String key;
    private final long startedAt = System.currentTimeMillis();

    private final LongAdder connections = new LongAdder();
    private final LongAdder activeConnections = new LongAdder();
    private final LongAdder failedConnections = new LongAdder();
    private final LongAdder upstreamBytes = new LongAdder();
    private final LongAdder downstreamBytes = new LongAdder();
    private final LongAdder droppedChunks = new LongAdder();
    private final LongAdder latencyChunks = new LongAdder();
    private final LongAdder throttledChunks = new LongAdder();

    public ProxyMetrics(String key) {
        this.key = key;
    }

    /** A shared sink used when no real proxy is associated (e.g. unit tests). */
    public static ProxyMetrics noop() {
        return NOOP;
    }

    public String getKey() {
        return key;
    }

    public void connectionOpened() {
        connections.increment();
        activeConnections.increment();
    }

    public void connectionClosed() {
        activeConnections.decrement();
    }

    public void connectionFailed() {
        failedConnections.increment();
    }

    public void addUpstreamBytes(long bytes) {
        if (bytes > 0) {
            upstreamBytes.add(bytes);
        }
    }

    public void addDownstreamBytes(long bytes) {
        if (bytes > 0) {
            downstreamBytes.add(bytes);
        }
    }

    public void chunkDropped() {
        droppedChunks.increment();
    }

    public void chunkDelayed() {
        latencyChunks.increment();
    }

    public void chunkThrottled() {
        throttledChunks.increment();
    }

    /** Wraps a destination stream so every byte written is counted as upstream (client -> target). */
    public OutputStream countUpstream(OutputStream out) {
        return new CountingOutputStream(out, this::addUpstreamBytes);
    }

    /** Wraps a destination stream so every byte written is counted as downstream (target -> client). */
    public OutputStream countDownstream(OutputStream out) {
        return new CountingOutputStream(out, this::addDownstreamBytes);
    }

    public long getConnections() {
        return connections.sum();
    }

    public long getActiveConnections() {
        return activeConnections.sum();
    }

    public long getFailedConnections() {
        return failedConnections.sum();
    }

    public long getUpstreamBytes() {
        return upstreamBytes.sum();
    }

    public long getDownstreamBytes() {
        return downstreamBytes.sum();
    }

    public long getDroppedChunks() {
        return droppedChunks.sum();
    }

    public long getLatencyChunks() {
        return latencyChunks.sum();
    }

    public long getThrottledChunks() {
        return throttledChunks.sum();
    }

    public long getStartedAt() {
        return startedAt;
    }

    public long getUptimeMs() {
        return System.currentTimeMillis() - startedAt;
    }
}
