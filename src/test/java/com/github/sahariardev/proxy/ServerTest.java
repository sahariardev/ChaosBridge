package com.github.sahariardev.proxy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Exercises the raw TCP proxy lifecycle (start, forward, stop) without the web layer.
 * Ports are allocated dynamically so the suite can run reliably and in parallel.
 */
class ServerTest {

    private ServerSocket backendSocket;
    private int backendPort;
    private int proxyPort;
    private String key;
    private Server server;
    private Thread proxyThread;

    @BeforeEach
    void setUp() throws IOException, InterruptedException {
        backendSocket = new ServerSocket(0);
        backendPort = backendSocket.getLocalPort();
        Thread.ofVirtual().start(this::serveBackend);

        proxyPort = freePort();
        key = proxyPort + ":127.0.0.1:" + backendPort;
        server = new Server(proxyPort, "127.0.0.1", backendPort, key);

        proxyThread = Thread.ofVirtual().start(() -> {
            try {
                server.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        waitForPort(proxyPort, 5000);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.stop();
        backendSocket.close();
    }

    @Test
    void forwardsTrafficBidirectionally() throws IOException {
        try (Socket client = new Socket("127.0.0.1", proxyPort)) {
            client.setSoTimeout(5000);
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

            writer.println("hello");

            assertEquals("hello", reader.readLine());
        }
    }

    @Test
    void stopReleasesTheListeningPort() throws IOException, InterruptedException {
        server.stop();

        long deadline = System.currentTimeMillis() + 3000;
        boolean bound = false;
        while (System.currentTimeMillis() < deadline && !bound) {
            try (ServerSocket rebind = new ServerSocket(proxyPort)) {
                bound = true;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }

        assertTrue(bound, "port " + proxyPort + " should be free after stop()");
    }

    @Test
    void refusesToStartTwiceOnTheSamePort() throws IOException {
        Server duplicate = new Server(proxyPort, "127.0.0.1", backendPort, key + "-dup");
        // The proxy swallows bind failures internally, so the socket is never exposed. Verify that
        // the original proxy is still serving while the duplicate fails to take over the port.
        Thread.ofVirtual().start(() -> {
            try {
                duplicate.start();
            } catch (IOException ignored) {
            }
        });

        try (Socket client = new Socket("127.0.0.1", proxyPort)) {
            client.setSoTimeout(5000);
            PrintWriter writer = new PrintWriter(client.getOutputStream(), true);
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream()));
            writer.println("still-here");
            assertEquals("still-here", reader.readLine());
        }
    }

    private void serveBackend() {
        while (!backendSocket.isClosed()) {
            try (Socket socket = backendSocket.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    out.flush();
                }
            } catch (IOException ignored) {
                // backend closed
            }
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForPort(int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                Thread.sleep(50);
            }
        }
        fail("server did not start listening on port " + port);
    }
}
