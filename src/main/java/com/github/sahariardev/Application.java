package com.github.sahariardev;

import io.micronaut.runtime.Micronaut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws IOException {
        logger.info("Application started");
        var context  = Micronaut.run(Application.class, args);

        var env = context.getEnvironment();

        System.out.println("📄 Property Sources: " + env.getPropertySources());
        System.out.println("🌐 Server port: " + env.getProperty("micronaut.server.port", Integer.class).orElse(-1));
    }
}