package com.github.sahariardev.pipeline;

import com.github.sahariardev.StreamUtil;
import com.github.sahariardev.chaos.Chaos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Pipeline {
    private static final Logger log = LoggerFactory.getLogger(Pipeline.class);
    private final List<Chaos> chaosList;

    private final String name;

    private Pipeline(String name, List<Chaos> chaosList) {
        this.name = name;
        this.chaosList = chaosList;
    }

    public void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        log.info("{} pipeline started copying", name);

        InputStream previous = inputStream;
        try (ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Chaos chaos : chaosList) {
                log.info("{} chaos copying", chaos.getClass().getSimpleName());
                final InputStream currentInputStream = previous;
                executorService.execute(() -> {
                    try {
                        chaos.write(currentInputStream);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
                previous = chaos.getInputStream();
            }

            StreamUtil.copyStream(previous, outputStream, 8 * 1024);
        }
    }

    public String getName() {
        return name;
    }

    public static class Builder {
        private String name;
        private final List<Chaos> chaosList = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder addLast(Chaos chaos) {
            chaosList.add(chaos);
            return this;
        }

        public Pipeline build() {
            return new Pipeline(name, chaosList);
        }
    }
}
