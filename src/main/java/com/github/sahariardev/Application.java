package com.github.sahariardev;

import com.github.sahariardev.proxy.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws IOException {
        logger.info("Application started");
        Server server = new Server();
        server.start(1081, "httpforever.com", 80);
    }
}