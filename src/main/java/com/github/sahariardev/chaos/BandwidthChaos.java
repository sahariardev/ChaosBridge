package com.github.sahariardev.chaos;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BandwidthChaos extends EmptyChaos {

    private final Logger logger = LoggerFactory.getLogger(EmptyChaos.class);

    private final int bandwidth;

    public BandwidthChaos(int bandwidth) throws IOException {
        super();

        //bandwidth is in bytePerSecond
        this.bandwidth = bandwidth;
    }

    @Override
    public void write(InputStream inputStream) throws IOException {
        logger.info("applying bandwidth chaos from {} to {}", inputStream.getClass().getName(), outputStream.getClass().getName());
        byte[] buffer = new byte[bandwidth];
        try (inputStream; outputStream) {

            while (!Thread.currentThread().isInterrupted()) {
                int read = inputStream.read(buffer);

                if (read == -1) {
                    break;
                }

                outputStream.write(buffer, 0, read);
                outputStream.flush();

                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    logger.warn("Interrupted during sleep");
                    Thread.currentThread().interrupt(); // Preserve interrupt status
                    break;
                }
            }


        } catch (SocketException e) {
            if ("Socket closed".equals(e.getMessage())) {
                logger.error("Socket closed", e);
            } else {
                throw e;
            }
        }
    }

    @Override
    public void createNewChaos(ObjectNode objectNode) {
        super.createNewChaos(objectNode);
    }

    @Override
    public ChaosConfig chaosConfig() {
        ChaosConfig chaosConfig = new ChaosConfig(getName());
        chaosConfig.addField("bytePerSecond");
        return chaosConfig;
    }

    @Override
    public String getName() {
        return "bandwidthChaos";
    }
}
