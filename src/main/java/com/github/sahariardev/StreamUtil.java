package com.github.sahariardev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtil {

    private static final Logger log = LoggerFactory.getLogger(StreamUtil.class);

    public static void copyStream(InputStream inputStream, OutputStream outputStream, int bufferSize) throws IOException {
        log.info("copying from {} to {}", inputStream.getClass().getName(), outputStream.getClass().getName());
        byte[] buffer = new byte[bufferSize];
        int read;

        try (inputStream; outputStream) {
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                outputStream.flush();
            }
        }
    }
}
