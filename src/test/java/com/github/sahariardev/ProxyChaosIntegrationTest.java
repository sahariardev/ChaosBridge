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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * True end-to-end test: boots the Micronaut HTTP API, an echo back-end and the real TCP proxy.
 * Traffic is driven through raw sockets so latency/packet-loss/bandwidth chaos is observed on the wire.
 */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@SuppressWarnings({"unchecked", "rawtypes"})
class ProxyChaosIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    private ServerSocket echoServer;
    private Thread echoAcceptor;
    private int echoPort;

    @BeforeEach
    void startEchoServer() throws IOException {
        echoServer = new ServerSocket(0);
        echoPort = echoServer.getLocalPort();
        echoAcceptor = Thread.ofVirtual().start(() -> {
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
                    // client disconnected; keep accepting
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
    void proxiesTrafficWithoutChaos() throws Exception {
        int proxyPort = createProxy();

        assertEquals("ping", roundTrip(proxyPort, "ping", 5000));
    }

    @Test
    void bandwidthChaosStillDeliversTraffic() throws Exception {
        int proxyPort = createProxy();
        String key = keyFor(proxyPort);
        applyChaos(key, Map.of("chaosType", "BANDWIDTH", "line", "downstream", "bytePerSecond", "1024"));

        assertEquals("ping", roundTrip(proxyPort, "ping", 5000));
    }

    @Test
    void latencyChaosDelaysDownstreamTraffic() throws Exception {
        int proxyPort = createProxy();
        String key = keyFor(proxyPort);
        applyChaos(key, Map.of("chaosType", "LATENCY", "line", "downstream", "latency", "1"));

        long start = System.nanoTime();
        String reply = roundTrip(proxyPort, "ping", 8000);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertEquals("ping", reply);
        assertTrue(elapsedMs >= 900, "expected at least ~1s latency, but got " + elapsedMs + "ms");
    }

    @Test
    void packetLossChaosWithTotalLossDropsDownstreamTraffic() throws Exception {
        int proxyPort = createProxy();
        String key = keyFor(proxyPort);
        applyChaos(key, Map.of("chaosType", "PACKET_LOSS", "line", "downstream", "packetLossRate", "1.0"));

        assertThrows(SocketTimeoutException.class, () -> roundTrip(proxyPort, "ping", 1500));
    }

    @Test
    void removingChaosRestoresTraffic() throws Exception {
        int proxyPort = createProxy();
        String key = keyFor(proxyPort);
        applyChaos(key, Map.of("chaosType", "PACKET_LOSS", "line", "downstream", "packetLossRate", "1.0"));

        assertThrows(SocketTimeoutException.class, () -> roundTrip(proxyPort, "ping", 1500));

        String chaosId = firstChaosId(key);
        HttpResponse<Map> removed = client.toBlocking()
                .exchange(HttpRequest.DELETE("/removeChaos/" + key + "/" + chaosId), Map.class);
        assertEquals(HttpStatus.OK, removed.getStatus());

        assertEquals("ping", roundTrip(proxyPort, "ping", 5000));
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

    private String keyFor(int proxyPort) {
        return proxyPort + ":127.0.0.1:" + echoPort;
    }

    private void applyChaos(String key, Map<String, String> chaos) {
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.POST("/addChaos/" + key, chaos), Map.class);
        assertEquals(HttpStatus.OK, response.getStatus());
        assertEquals("success", response.body().get("status"));
    }

    private String firstChaosId(String key) {
        HttpResponse<Map> response = client.toBlocking().exchange(HttpRequest.GET("/allChaos/" + key), Map.class);
        List<Map<String, Object>> chaosList = (List<Map<String, Object>>) response.body().get("message");
        return String.valueOf(chaosList.get(0).get("id"));
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static void waitForPort(int port, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            try (Socket socket = new Socket("127.0.0.1", port)) {
                return;
            } catch (IOException e) {
                last = e;
                Thread.sleep(50);
            }
        }
        fail("proxy did not start listening on port " + port + ": " + last);
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
