package com.github.sahariardev.proxy.ReactorNettyChunkedTcpProxy;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleTcpProxy {

    private static final int PROXY_PORT = 1081; // Port where proxy listens
    private static final String TARGET_HOST = "aggdb.therapbd.net"; // Target backend
    private static final int TARGET_PORT = 1521; // Target server port

    private static final int CHUNK_SIZE = 10 * 1024; // Chunk size in bytes
    private static final int CHUNK_DELAY_MS = 100; // Delay between chunks

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PROXY_PORT);
        ExecutorService executor = Executors.newCachedThreadPool();

        System.out.println("TCP Proxy started on port " + PROXY_PORT);

        while (true) {
            Socket clientSocket = serverSocket.accept();
            executor.submit(() -> handleClient(clientSocket));
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (
                Socket targetSocket = new Socket(TARGET_HOST, TARGET_PORT);
                InputStream clientInput = clientSocket.getInputStream();
                OutputStream clientOutput = clientSocket.getOutputStream();
                InputStream targetInput = targetSocket.getInputStream();
                OutputStream targetOutput = targetSocket.getOutputStream()
        ) {
            Thread clientToTarget = new Thread(() -> forwardWithChunking(clientInput, targetOutput));
            Thread targetToClient = new Thread(() -> forwardWithChunking(targetInput, clientOutput));

            clientToTarget.start();
            targetToClient.start();

            clientToTarget.join();
            targetToClient.join();
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }

    private static void forwardWithChunking(InputStream input, OutputStream output) {
        byte[] buffer = new byte[CHUNK_SIZE];

        try {
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
                Thread.sleep(CHUNK_DELAY_MS); // Delay between chunks
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error forwarding data: " + e.getMessage());
        }
    }
}

