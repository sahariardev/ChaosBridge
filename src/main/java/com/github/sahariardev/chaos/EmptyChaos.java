package com.github.sahariardev.chaos;

import java.io.*;

public class EmptyChaos implements Chaos {
    private final PipedInputStream inputStream;

    private final PipedOutputStream outputStream;

    public EmptyChaos() throws IOException {
        this.outputStream = new PipedOutputStream();
        this.inputStream = new PipedInputStream(outputStream);
    }

    @Override
    public void write(InputStream inputStream) throws IOException {
        copyStream(inputStream, outputStream);
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

}
