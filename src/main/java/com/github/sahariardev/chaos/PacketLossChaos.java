package com.github.sahariardev.chaos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Random;

public class PacketLossChaos extends EmptyChaos {

    private static final Logger logger = LoggerFactory.getLogger(LatencyChaos.class);

    private static final int DEFAULT_CHUNK_SIZE = 1024 * 8;

    private final double packetLossRate;

    public PacketLossChaos(double packetLossRate) throws IOException {
        super();
        this.packetLossRate = packetLossRate;
    }

    @Override
    public void write(InputStream inputStream) throws IOException {
        logger.info("applying packetloss chaos copying from {} to {}", inputStream.getClass().getName(), outputStream.getClass().getName());

        if (packetLossRate < 0.0 || packetLossRate > 1.0) {
            throw new IllegalArgumentException("Packet loss rate must be between 0.0 and 1.0");
        }

        Random random = new Random();
        byte[] buffer = new byte[DEFAULT_CHUNK_SIZE];
        try (inputStream; outputStream) {

            while (!Thread.currentThread().isInterrupted()) {
                int read = inputStream.read(buffer);

                if (read == -1) {
                    break;
                }

                if (random.nextDouble() < packetLossRate) {
                    logger.info("dropping packet of size {} bytes", read);

                    continue;
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
