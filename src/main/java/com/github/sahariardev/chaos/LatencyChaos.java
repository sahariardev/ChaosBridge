package com.github.sahariardev.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.concurrent.TimeUnit;

public class LatencyChaos extends EmptyChaos {

    private static final Logger logger = LoggerFactory.getLogger(LatencyChaos.class);

    private static final int DEFAULT_CHUNK_SIZE = 1024 * 8;

    private final int latency;

    public LatencyChaos(int latency) throws IOException {
        super();
        this.latency = latency;
    }

    @Override
    public void write(InputStream inputStream) throws IOException {
        logger.info("applying latency chaos copying from {} to {}", inputStream.getClass().getName(), outputStream.getClass().getName());
        byte[] buffer = new byte[DEFAULT_CHUNK_SIZE];
        try (inputStream; outputStream) {

            while (!Thread.currentThread().isInterrupted()) {
                int read = inputStream.read(buffer);

                if (read == -1) {
                    break;
                }

                try {
                    TimeUnit.SECONDS.sleep(latency);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted during sleep");
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    break;
                }

                outputStream.write(buffer, 0, read);
                outputStream.flush();
            }


        } catch (SocketException e) {
            if ("Socket closed".equals(e.getMessage())) {
                logger.error("Socket closed", e);
            } else {
                throw e;
            }
        }
    }
}
