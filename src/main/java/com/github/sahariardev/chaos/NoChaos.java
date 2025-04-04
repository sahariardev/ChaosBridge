package com.github.sahariardev.chaos;

import java.io.*;

public class NoChaos implements Chaos {
    private final PipedInputStream inputStream;

    private final PipedOutputStream outputStream;

    public NoChaos() throws IOException {
        this.outputStream = new PipedOutputStream();
        this.inputStream = new PipedInputStream(outputStream);
    }

    @Override
    public void write(InputStream inputStream) throws IOException {
        copyStream(inputStream, outputStream);
    }

    @Override
    public void read(OutputStream outputStream) throws IOException {
        copyStream(inputStream, outputStream);
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

}
