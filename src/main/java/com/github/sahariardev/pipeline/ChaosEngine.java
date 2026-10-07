package com.github.sahariardev.pipeline;

import com.github.sahariardev.chaos.ChaosPlan;
import com.github.sahariardev.common.Store;
import com.github.sahariardev.metrics.ProxyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Copies bytes from a source to a destination while applying the chaos profiles that are active
 * <em>at the moment each chunk is read</em>.
 * <p>
 * The previous implementation built a fixed chain of piped streams when the connection was accepted,
 * which meant profiles added afterwards never affected already-open connections. Re-reading the plan
 * per chunk makes latency, packet loss and bandwidth limits apply immediately, even to long-lived
 * keep-alive connections.
 */
public class ChaosEngine {

    private static final Logger log = LoggerFactory.getLogger(ChaosEngine.class);
    private static final int BUFFER_SIZE = 8 * 1024;

    private final String key;
    private final String line;
    private final ProxyMetrics metrics;
    private final Random random = new Random();

    public ChaosEngine(String key, String line, ProxyMetrics metrics) {
        this.key = key;
        this.line = line;
        this.metrics = metrics;
    }

    public void transfer(InputStream inputStream, OutputStream outputStream) {
        log.debug("{} pipeline started copying for {}", line, key);
        byte[] buffer = new byte[BUFFER_SIZE];

        try (inputStream; outputStream) {
            while (!Thread.currentThread().isInterrupted()) {
                // Size the read from the currently active bandwidth limit (if any).
                int cap = capFor(ChaosPlan.from(Store.INSTANCE.get(key), line));

                int read = inputStream.read(buffer, 0, cap);
                if (read == -1) {
                    break;
                }

                // Re-read the plan *after* the chunk arrives so a profile added while this connection
                // was blocked on read takes effect on this very chunk.
                ChaosPlan plan = ChaosPlan.from(Store.INSTANCE.get(key), line);

                if (plan.packetLossEnabled() && random.nextDouble() < plan.packetLossRate()) {
                    log.debug("dropping packet of size {} bytes for {}", read, key);
                    metrics.chunkDropped();
                    continue;
                }

                if (plan.latencySeconds() > 0) {
                    if (!sleepSeconds(plan.latencySeconds())) {
                        break;
                    }
                    metrics.chunkDelayed();
                }

                outputStream.write(buffer, 0, read);
                outputStream.flush();

                if (plan.bytesPerSecond() != null) {
                    metrics.chunkThrottled();
                    if (!sleepSeconds(1)) {
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            if ("Socket closed".equals(e.getMessage())) {
                log.debug("{} socket closed for {}", line, key);
            } else {
                log.debug("{} copy failed for {}", line, key, e);
            }
        } catch (IOException e) {
            // A peer closing the connection surfaces as an IOException; that is the normal end of a transfer.
            log.debug("{} copy ended for {}: {}", line, key, e.getMessage());
        }
    }

    private static int capFor(ChaosPlan plan) {
        return plan.bytesPerSecond() != null
                ? Math.max(1, Math.min(BUFFER_SIZE, plan.bytesPerSecond()))
                : BUFFER_SIZE;
    }

    private boolean sleepSeconds(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
