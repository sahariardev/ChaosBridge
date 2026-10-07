package com.github.sahariardev.proxy;

import com.github.sahariardev.common.Constant;
import com.github.sahariardev.metrics.MetricsRegistry;
import com.github.sahariardev.metrics.ProxyMetrics;
import com.github.sahariardev.pipeline.ChaosEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
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
        } catch (SocketException e) {
            // Closing the server socket is how stop() unblocks accept(); that is not an error.
            if (running) {
                logger.error("Proxy {} stopped unexpectedly", key, e);
            } else {
                logger.debug("Proxy {} listener closed", key);
            }
        } catch (Exception e) {
            logger.error("Error happened {}", key, e);
        }
    }

    public void handleClient(Socket clientSocket, String serverHost, int serverPort) {
        Socket targetSocket = null;
        ExecutorService copyExecutor = Executors.newVirtualThreadPerTaskExecutor();
        ProxyMetrics metrics = MetricsRegistry.INSTANCE.register(key);
        metrics.connectionOpened();

        try {
            targetSocket = new Socket(serverHost, serverPort);

            InputStream clientInputStream = clientSocket.getInputStream();
            OutputStream clientOutputStream = metrics.countDownstream(clientSocket.getOutputStream());
            InputStream targetInputStream = targetSocket.getInputStream();
            OutputStream targetOutputStream = metrics.countUpstream(targetSocket.getOutputStream());

            // Chaos is evaluated per chunk, so profiles added or removed while this connection is open
            // take effect on the very next chunk (see ChaosEngine).
            ChaosEngine upStreamEngine = new ChaosEngine(key, Constant.UPSTREAM, metrics);
            ChaosEngine downStreamEngine = new ChaosEngine(key, Constant.DOWNSTREAM, metrics);

            Future<?> upStreamFuture = copyExecutor.submit(() -> upStreamEngine.transfer(clientInputStream, targetOutputStream));
            Future<?> downStreamFuture = copyExecutor.submit(() -> downStreamEngine.transfer(targetInputStream, clientOutputStream));

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
            metrics.connectionFailed();
        } finally {
            metrics.connectionClosed();
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
}
