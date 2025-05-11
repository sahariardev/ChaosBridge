package com.github.sahariardev.proxy;

import com.github.sahariardev.chaos.Chaos;
import com.github.sahariardev.chaos.ChaosFactory;
import com.github.sahariardev.common.Constant;
import com.github.sahariardev.common.Store;
import com.github.sahariardev.pipeline.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class Server {

    private static final Logger logger = LoggerFactory.getLogger(Server.class);

    private final int port;

    private final String serverHost;

    private final int serverPort;

    private final String key;

    private volatile boolean running = true;

    private ServerSocket serverSocket;

    private ExecutorService executorService;

    public Server(int port, String serverHost, int serverPort, String key) {
        this.port = port;
        this.serverHost = serverHost;
        this.serverPort = serverPort;
        this.key = key;
    }

    public void start() throws IOException {
        try {
            executorService = Executors.newVirtualThreadPerTaskExecutor();
            serverSocket = new ServerSocket(port);

            Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

            while (running) {
                Socket clientSocket = serverSocket.accept();
                executorService.execute(() -> {
                    handleClient(clientSocket, serverHost, serverPort);
                });
            }

            stop();
        } catch (Exception e) {
            logger.error("Error happened {}", key, e);
        }
    }

    public void handleClient(Socket clientSocket, String serverHost, int serverPort) {
        Socket targetSocket = null;
        ExecutorService copyExecutor = Executors.newVirtualThreadPerTaskExecutor();

        try {
            targetSocket = new Socket(serverHost, serverPort);

            InputStream clientInputStream = clientSocket.getInputStream();
            OutputStream clientOutputStream = clientSocket.getOutputStream();
            InputStream targetInputStream = targetSocket.getInputStream();
            OutputStream targetOutputStream = targetSocket.getOutputStream();

            Pipeline upStreamPipeLine = new Pipeline.Builder().name("upstream").build();
            Pipeline downStreamPipeLine = new Pipeline.Builder().name("downstream").build();

            List<Map<String, Object>> chaosConfig = Store.INSTANCE.get(key);
            for (Map<String, Object> chaosConfigNode : chaosConfig) {
                Chaos chaos = ChaosFactory.buildChaos(chaosConfigNode);
                if (chaosConfigNode.get(Constant.LINE).equals(Constant.DOWNSTREAM)) {
                    downStreamPipeLine.addChaos(chaos);
                } else if (chaosConfigNode.get(Constant.LINE).equals(Constant.UPSTREAM)) {
                    upStreamPipeLine.addChaos(chaos);
                }
            }

            Future<?> upStreamFuture = copyExecutor.submit(() -> copyStream(clientInputStream, targetOutputStream, upStreamPipeLine));
            Future<?> downStreamFuture = copyExecutor.submit(() -> copyStream(targetInputStream, clientOutputStream, downStreamPipeLine));

            // Wait for one direction to complete, then cancel the other
            while (true) {
                try {
                    upStreamFuture.get(100, TimeUnit.MILLISECONDS);
                    downStreamFuture.cancel(true);
                    break;
                } catch (TimeoutException ignored) {
                }
                try {
                    downStreamFuture.get(100, TimeUnit.MILLISECONDS);
                    upStreamFuture.cancel(true);
                    break;
                } catch (TimeoutException ignored) {
                }
            }

        } catch (Exception e) {
            logger.warn("Exception in handleClient", e);
        } finally {
            copyExecutor.shutdownNow();
            try {
                if (targetSocket != null && !targetSocket.isClosed()) targetSocket.close();
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException ignored) {}
        }
    }


    public void stop() {
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (executorService != null) {
            executorService.shutdown();

            try {
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
            }
        }
    }

    private void copyStream(InputStream inputStream, OutputStream outputStream, Pipeline pipeline) {
        try {
            pipeline.copy(inputStream, outputStream);
        } catch (IOException e) {
            logger.debug("Failed to copy stream", e);
        }
    }
}
