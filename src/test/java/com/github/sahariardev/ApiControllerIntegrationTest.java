package com.github.sahariardev;

import com.github.sahariardev.common.Store;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boots the real Micronaut HTTP server (random port) and exercises the public REST API end to end.
 */
@MicronautTest
@Property(name = "micronaut.server.port", value = "-1")
@SuppressWarnings({"unchecked", "rawtypes"})
class ApiControllerIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @AfterEach
    void removeAllProxies() {
        for (String key : Store.INSTANCE.keys()) {
            client.toBlocking().exchange(HttpRequest.DELETE("/proxy/" + key), Map.class);
        }
    }

    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void homePageRendersTheVelocityView() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/"), String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertNotNull(response.body());
        assertTrue(response.body().contains("Chaos Bridge"), "home page should render the Velocity template");
    }

    @Test
    void staticResourcesAreServed() {
        HttpResponse<String> response = client.toBlocking().exchange(HttpRequest.GET("/main.js"), String.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        assertTrue(response.body().contains("loadProxies"));
    }

    @Test
    void chaosConfigExposesAllPublicChaosTypes() {
        HttpResponse<List> response = client.toBlocking().exchange(HttpRequest.GET("/chaosConfig"), List.class);

        assertEquals(HttpStatus.OK, response.getStatus());
        List<Map<String, Object>> configs = response.body();
        configs.forEach(config -> assertTrue(config.containsKey("fields")));

        List<Object> types = configs.stream().map(config -> config.get("type")).toList();
        assertTrue(types.contains("BANDWIDTH"));
        assertTrue(types.contains("LATENCY"));
        assertTrue(types.contains("PACKET_LOSS"));
        assertFalse(types.contains("EMPTY"), "internal-only chaos types must not be exposed");
    }

    @Test
    void proxyCanBeCreatedListedAndDeleted() throws IOException {
        int proxyPort = freePort();
        int targetPort = freePort();

        Map<String, String> body = Map.of(
                "port", String.valueOf(proxyPort),
                "serverHost", "localhost",
                "serverPort", String.valueOf(targetPort)
        );

        HttpResponse<Map> created = client.toBlocking().exchange(HttpRequest.POST("/proxy", body), Map.class);
        assertEquals(HttpStatus.OK, created.getStatus());
        assertEquals("success", created.body().get("status"));

        String key = String.valueOf(created.body().get("key"));
        assertEquals(proxyPort + ":localhost:" + targetPort, key);

        HttpResponse<Map> listed = client.toBlocking().exchange(HttpRequest.GET("/proxy"), Map.class);
        List<Map<String, String>> proxies = (List<Map<String, String>>) listed.body().get("data");
        assertTrue(proxies.stream().anyMatch(proxy -> key.equals(proxy.get("key"))));
        Map<String, String> proxy = proxies.stream().filter(p -> key.equals(p.get("key"))).findFirst().orElseThrow();
        assertEquals(String.valueOf(proxyPort), proxy.get("port"));
        assertEquals("localhost", proxy.get("serverHost"));

        HttpResponse<Map> deleted = client.toBlocking().exchange(HttpRequest.DELETE("/proxy/" + key), Map.class);
        assertEquals(HttpStatus.OK, deleted.getStatus());
        assertEquals("success", deleted.body().get("status"));

        HttpResponse<Map> listedAgain = client.toBlocking().exchange(HttpRequest.GET("/proxy"), Map.class);
        List<Map<String, String>> remaining = (List<Map<String, String>>) listedAgain.body().get("data");
        assertFalse(remaining.stream().anyMatch(p -> key.equals(p.get("key"))));
    }

    @Test
    void chaosCanBeAddedListedAndRemoved() throws IOException {
        String key = createProxy();

        Map<String, String> chaos = Map.of(
                "chaosType", "LATENCY",
                "line", "upstream",
                "latency", "1"
        );

        HttpResponse<Map> added = client.toBlocking().exchange(HttpRequest.POST("/addChaos/" + key, chaos), Map.class);
        assertEquals(HttpStatus.OK, added.getStatus());
        assertEquals("success", added.body().get("status"));

        HttpResponse<Map> listed = client.toBlocking().exchange(HttpRequest.GET("/allChaos/" + key), Map.class);
        List<Map<String, Object>> chaosList = (List<Map<String, Object>>) listed.body().get("message");
        assertEquals(1, chaosList.size());
        assertEquals("LATENCY", chaosList.get(0).get("type"));
        String chaosId = String.valueOf(chaosList.get(0).get("id"));
        assertNotNull(chaosId);

        HttpResponse<Map> removed = client.toBlocking()
                .exchange(HttpRequest.DELETE("/removeChaos/" + key + "/" + chaosId), Map.class);
        assertEquals(HttpStatus.OK, removed.getStatus());

        HttpResponse<Map> listedAgain = client.toBlocking().exchange(HttpRequest.GET("/allChaos/" + key), Map.class);
        List<Map<String, Object>> remaining = (List<Map<String, Object>>) listedAgain.body().get("message");
        assertTrue(remaining.isEmpty());
    }

    @Test
    void rejectsProxyRequestWithNonNumericPort() {
        Map<String, String> body = Map.of(
                "port", "not-a-number",
                "serverHost", "localhost",
                "serverPort", "80"
        );

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/proxy", body), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void rejectsProxyRequestWithMissingFields() {
        Map<String, String> body = Map.of("port", "8080");

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/proxy", body), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void rejectsUnknownChaosType() throws IOException {
        String key = createProxy();
        Map<String, String> chaos = Map.of("chaosType", "NOT_A_CHAOS", "line", "upstream");

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/addChaos/" + key, chaos), Map.class));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void rejectsChaosForUnknownProxy() {
        Map<String, String> chaos = Map.of("chaosType", "LATENCY", "line", "upstream", "latency", "1");

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class,
                () -> client.toBlocking().exchange(HttpRequest.POST("/addChaos/1:2:3", chaos), Map.class));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    private String createProxy() throws IOException {
        Map<String, String> body = Map.of(
                "port", String.valueOf(freePort()),
                "serverHost", "localhost",
                "serverPort", String.valueOf(freePort())
        );
        HttpResponse<Map> created = client.toBlocking().exchange(HttpRequest.POST("/proxy", body), Map.class);
        assertEquals(HttpStatus.OK, created.getStatus());
        return String.valueOf(created.body().get("key"));
    }
}
