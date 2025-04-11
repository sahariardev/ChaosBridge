package com.github.sahariardev.proxy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.sahariardev.chaos.Chaos;
import com.github.sahariardev.chaos.ChaosFactory;
import com.github.sahariardev.common.Constant;
import com.github.sahariardev.common.Store;
import com.github.sahariardev.pipeline.Pipeline;
import io.netty.util.internal.logging.Slf4JLoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private final int port;

    private final String serverHost;

    private final int serverPort;

    private final String key;

    public Server(int port, String serverHost, int serverPort, String key) {
        this.port = port;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.key = key;
    }

    public void start() throws IOException {
        try (
                ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
                ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {
                Socket clientSocket = serverSocket.accept();
                executorService.execute(() -> {
                    handleClient(clientSocket, serverHost, serverPort);
                });
            }
        }
    }

    public void handleClient(Socket clientSocket, String serverHost, int serverPort) {
        try (
                Socket targetSocket = new Socket(serverHost, serverPort);
                InputStream clientInputStream = clientSocket.getInputStream();
                OutputStream clientOutputStream = clientSocket.getOutputStream();
                InputStream targetInputStream = targetSocket.getInputStream();
                OutputStream targetOutputStream = targetSocket.getOutputStream();
                ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor()
        ) {
            Pipeline upStreamPipeLine = new Pipeline.Builder()
                    .name("upstream")
                    .build();

            Pipeline downStreamPipeLine = new Pipeline.Builder()
                    .name("downstream")
                    .build();

            List<ObjectNode> chaosConfig = Store.INSTANCE.get(key);

            for (ObjectNode chaosConfigNode : chaosConfig) {

                Chaos chaos = ChaosFactory.buildChaos(chaosConfigNode);

                if (chaosConfigNode.get(Constant.LINE).asText().equals(Constant.DOWNSTREAM)) {
                    downStreamPipeLine.addChaos(chaos);

                } else if (chaosConfigNode.get(Constant.LINE).asText().equals(Constant.UPSTREAM)) {
                    upStreamPipeLine.addChaos(chaos);

                } else {
                    throw new IllegalStateException("Unexpected value: " + chaosConfigNode.get(Constant.LINE));
                }
            }

            Future<?> upStreamFuture = executorService.submit(() -> {
                copyStream(clientInputStream, targetOutputStream, upStreamPipeLine);
            });
            Future<?> downStreamFuture = executorService.submit(() -> {
                copyStream(targetInputStream, clientOutputStream, downStreamPipeLine);
            });

            upStreamFuture.get();
            downStreamFuture.get();
        } catch (ExecutionException | InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void copyStream(InputStream inputStream, OutputStream outputStream, Pipeline pipeline) {
        try {
            pipeline.copy(inputStream, outputStream);
        } catch (IOException e) {
            logger.debug("Failed to copy stream", e);
        }
    }
}
