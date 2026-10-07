package com.github.sahariardev.metrics;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.LongConsumer;

/**
 * Counts the bytes written through it and reports them to a {@link LongConsumer}. The bulk
 * {@link #write(byte[], int, int)} method is overridden so counting does not degrade to per-byte calls.
 */
public class CountingOutputStream extends FilterOutputStream {

    private final LongConsumer counter;

    public CountingOutputStream(OutputStream out, LongConsumer counter) {
        super(out);
        this.counter = counter;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        counter.accept(1);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        counter.accept(len);
    }

    @Override
    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
