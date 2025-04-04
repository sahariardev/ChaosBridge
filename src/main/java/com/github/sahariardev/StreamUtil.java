package com.github.sahariardev;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class StreamUtil {

    public static void copyStream(InputStream inputStream, OutputStream outputStream, int bufferSize) throws IOException {
        byte[] buffer = new byte[bufferSize];
        int read;

        try (inputStream; outputStream) {
            while ((read = inputStream.read(buffer)) != 0) {
                outputStream.write(buffer, 0, read);
                outputStream.flush();
            }
        }
    }
}
