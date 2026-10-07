package com.github.sahariardev.chaos;

import com.github.sahariardev.metrics.ProxyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

public class EmptyChaos implements Chaos {

    private static final Logger logger = LoggerFactory.getLogger(EmptyChaos.class);

    protected final PipedInputStream inputStream;

    protected final PipedOutputStream outputStream;

    protected final ProxyMetrics metrics;

    public EmptyChaos() throws IOException {
        this(ProxyMetrics.noop());
    }

    public EmptyChaos(ProxyMetrics metrics) throws IOException {
        this.metrics = metrics == null ? ProxyMetrics.noop() : metrics;
        this.outputStream = new PipedOutputStream();
        this.inputStream = new PipedInputStream(outputStream);
    }

    @Override
    public void write(InputStream inputStream) throws IOException {
        logger.info("applying empty chaos copying from {} to {}", inputStream.getClass().getName(), outputStream.getClass().getName());
        copyStream(inputStream, outputStream);
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }
}
