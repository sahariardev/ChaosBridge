package com.github.sahariardev;

import com.github.sahariardev.common.Store;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
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
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the metrics pipeline end to end: real traffic through the proxy is counted and exposed
 * through {@code /metrics}, {@code /metrics/{key}} and the Prometheus endpoint.
 */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@SuppressWarnings({"unchecked", "rawtypes"})
class MetricsIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    private ServerSocket echoServer;
    private int echoPort;

    @BeforeEach
    void startEchoServer() throws IOException {
        echoServer = new ServerSocket(0);
        echoPort = echoServer.getLocalPort();
        Thread.ofVirtual().start(() -> {
            while (!echoServer.isClosed()) {
                try (Socket socket = echoServer.accept()) {
                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (IOException ignored) {
                    // client disconnected
                }
            }
        });
    }

    @AfterEach
    void tearDown() throws IOException {
        for (String key : Store.INSTANCE.keys()) {
            client.toBlocking().exchange(HttpRequest.DELETE("/proxy/" + key), Map.class);
        }
        echoServer.close();
    }

    @Test
    void reportsTrafficMetricsForAProxy() throws Exception {
        int proxyPort = createProxy();

        assertEquals("hello", roundTrip(proxyPort, "hello", 5000));
        Thread.sleep(250);

        Map metrics = getMetrics(proxyPort);
        assertTrue(((Number) metrics.get("connections")).longValue() >= 1, "connections should be counted");
        assertTrue(((Number) metrics.get("upstreamBytes")).longValue() >= 5, "upstream bytes should be counted");
        assertTrue(((Number) metrics.get("downstreamBytes")).longValue() >= 5, "downstream bytes should be counted");
        assertEquals(0L, ((Number) metrics.get("activeConnections")).longValue(), "connection should be closed");
        assertEquals(echoPort + "", String.valueOf(metrics.get("serverPort")));
    }

    @Test
    void countsDroppedChunksWhenPacketLossIsApplied() throws Exception {
        int proxyPort = createProxy();
        String key = keyFor(proxyPort);
        applyChaos(key, Map.of("chaosType", "PACKET_LOSS", "line", "downstream", "packetLossRate", "1.0"));

        assertThrows(SocketTimeoutException.class, () -> roundTrip(proxyPort, "ping", 1200));
        Thread.sleep(250);

        Map metrics = getMetrics(proxyPort);
        assertTrue(((Number) metrics.get("droppedChunks")).longValue() >= 1, "dropped chunks should be counted");

        List<Map<String, Object>> chaos = (List<Map<String, Object>>) metrics.get("chaos");
        assertEquals(1, chaos.size());
        assertEquals(1L, ((Number) metrics.get("chaosCount")).longValue());
    }

    @Test
    void exposesAggregatesHostsAndPrometheus() throws Exception {
        int proxyPort = createProxy();
        roundTrip(proxyPort, "hi", 5000);
        Thread.sleep(250);

        HttpResponse<Map> all = client.toBlocking().exchange(HttpRequest.GET("/metrics"), Map.class);
        assertEquals(HttpStatus.OK, all.getStatus());

        Map totals = (Map) all.body().get("totals");
        assertTrue(((Number) totals.get("proxies")).longValue() >= 1);
        assertTrue(((Number) totals.get("connections")).longValue() >= 1);

        List hosts = (List) all.body().get("hosts");
        assertFalse(hosts.isEmpty(), "hosts aggregation should not be empty");

        HttpResponse<String> prometheus = client.toBlocking()
                .exchange(HttpRequest.GET("/prometheus").accept(io.micronaut.http.MediaType.TEXT_PLAIN), String.class);
        assertEquals(HttpStatus.OK, prometheus.getStatus());
        assertTrue(prometheus.body().contains("chaosbridge_connections_total"));
        assertTrue(prometheus.body().contains("chaosbridge_upstream_bytes_total"));
        assertTrue(prometheus.body().contains("host=\"127.0.0.1\""));
    }

    @Test
    void returnsNotFoundForUnknownProxyMetrics() {
        HttpResponse<String> response;
        try {
            response = client.toBlocking().exchange(HttpRequest.GET("/metrics/1:2:3"), String.class);
        } catch (io.micronaut.http.client.exceptions.HttpClientResponseException e) {
            assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
            return;
        }
        assertEquals(HttpStatus.NOT_FOUND, response.getStatus());
    }

    private int createProxy() throws IOException, InterruptedException {
        int proxyPort = freePort();
        Map<String, String> body = Map.of(
                "port", String.valueOf(proxyPort),
                "serverHost", "127.0.0.1",
                "serverPort", String.valueOf(echoPort)
        );
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST("/proxy", body), Map.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        waitForPort(proxyPort, 5000);
        return proxyPort;
    }

    private Map getMetrics(int proxyPort) {
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.GET("/metrics/" + keyFor(proxyPort)), Map.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        return response.body();
    }

    private void applyChaos(String key, Map<String, String> chaos) {
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST("/addChaos/" + key, chaos), Map.class);
        assertEquals(HttpStatus.OK, response.getStatus());
    }

    private String keyFor(int proxyPort) {
        return proxyPort + ":127.0.0.1:" + echoPort;
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
        throw new IllegalStateException("proxy did not start listening on port " + port);
    }

    private static String roundTrip(int port, String message, int soTimeout) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(soTimeout);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out.println(message);
            return in.readLine();
        }
    }
}
