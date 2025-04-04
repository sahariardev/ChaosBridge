package com.github.sahariardev.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Server {

    public void start(int port, String serverHost, int serverPort) throws IOException {
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
            Future<?> upStreamFuture = executorService.submit(() -> {
                copyStream(clientInputStream, targetOutputStream);
            });
            Future<?> downStreamFuture = executorService.submit(() -> {
                copyStream(targetInputStream, clientOutputStream);
            });

            upStreamFuture.get();
            downStreamFuture.get();
        } catch (ExecutionException | InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void copyStream(InputStream inputStream, OutputStream outputStream) {
        byte[] buffer = new byte[1024];
        int read;

        try {
            while ((read = inputStream.read(buffer)) != 0) {
                outputStream.write(buffer, 0, read);
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
