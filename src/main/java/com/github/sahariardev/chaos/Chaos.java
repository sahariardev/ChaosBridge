package com.github.sahariardev.chaos;

import com.github.sahariardev.StreamUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface Chaos {

    void write(InputStream inputStream) throws IOException;

    void read(OutputStream outputStream) throws IOException;

    InputStream getInputStream();

    OutputStream getOutputStream();

    default void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        StreamUtil.copyStream(inputStream, outputStream, 8 * 1024);
    }
}
