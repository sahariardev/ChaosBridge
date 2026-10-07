package com.github.sahariardev.web;

import com.github.sahariardev.chaos.ChaosConfig;
import com.github.sahariardev.chaos.ChaosType;
import com.github.sahariardev.common.Store;
import com.github.sahariardev.metrics.MetricsRegistry;
import com.github.sahariardev.metrics.ProxyMetrics;
import com.github.sahariardev.proxy.Server;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.views.View;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Stream;

@Controller("/")
public class ApiController {

    @Inject
    @Named("virtual-thread-executor")
    private ExecutorService executorService;

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);

    @View("home")
    @Get("/")
    public HttpResponse<?> home() {
        logger.info("[Get] home");
        return HttpResponse.ok();
    }

    @Get("/proxy")
    public HttpResponse<?> getAllProxy() {
        List<String> keys = Store.INSTANCE.keys();

        List<Map<String, String>> data = new ArrayList<>();

        for (String key : keys) {
            String[] parts = splitKey(key);
            if (parts == null) {
                logger.warn("Skipping malformed proxy key {}", key);
                continue;
            }
            Map<String, String> map = new HashMap<>();
            map.put("port", parts[0]);
            map.put("serverHost", parts[1]);
            map.put("serverPort", parts[2]);
            map.put("key", key);

            data.add(map);
        }

        Map<String, Object> model = new HashMap<>();
        model.put("data", data);
        return HttpResponse.ok(model);
    }

    @Post("/proxy")
    public HttpResponse<?> addProxy(@Body Map<String, String> formData) {
        logger.info("[POST] Creating new proxy with data {}", formData);

        String port = formData.get("port");
        String serverHost = formData.get("serverHost");
        String serverPort = formData.get("serverPort");

        if (port == null || port.isBlank() || serverHost == null || serverHost.isBlank()
                || serverPort == null || serverPort.isBlank()) {
            return HttpResponse.badRequest(error("'port', 'serverHost' and 'serverPort' are required"));
        }

        final int parsedPort;
        final int parsedServerPort;
        try {
            parsedPort = Integer.parseInt(port.trim());
            parsedServerPort = Integer.parseInt(serverPort.trim());
        } catch (NumberFormatException e) {
            return HttpResponse.badRequest(error("'port' and 'serverPort' must be valid integers"));
        }

        String host = serverHost.trim();
        String key = String.format("%s:%s:%s", parsedPort, host, parsedServerPort);
        Server server = new Server(parsedPort, host, parsedServerPort, key);

        Store.INSTANCE.addServer(key, server);
        MetricsRegistry.INSTANCE.register(key);

        executorService.execute(() -> {
            try {
                logger.info("starting new proxy with data {}", formData);
                server.start();
            } catch (IOException e) {
                logger.error("Error starting new proxy", e);
                throw new RuntimeException(e);
            }
        });

        logger.info("[POST] Created new proxy");

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("key", key);
        response.put("message", "Proxy started successfully " + formData);

        return HttpResponse.ok(response);
    }

    @Get("/chaosConfig")
    public HttpResponse<?> getChaosConfigs() {
        List<ChaosConfig> chaosConfigList = Stream.of(ChaosType.values()).filter(chaosType -> !chaosType.isForInternalUse())
                .map(ChaosType::getConfig).toList();
        return HttpResponse.ok(chaosConfigList);
    }

    @Delete("/proxy/{key}")
    public HttpResponse<?> deleteProxy(@PathVariable String key) {
        Server server = Store.INSTANCE.getServer(key);

        if (server != null) {
            server.stop();
        }

        Store.INSTANCE.remove(key);
        MetricsRegistry.INSTANCE.remove(key);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Stopped Server " + key + " data ");

        return HttpResponse.ok(response);
    }

    @Get("/allChaos/{key}")
    public HttpResponse<Map<String, Object>> allChaos(@PathVariable String key) {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", Store.INSTANCE.getChaosList(key));

        return HttpResponse.ok(response);
    }

    @Post("/addChaos/{key}")
    public HttpResponse<Map<String, String>> applyChaos(@PathVariable String key, @Body Map<String, String> formData) {
        if (Store.INSTANCE.getServer(key) == null) {
            return HttpResponse.notFound(error("No proxy found for key " + key));
        }

        String chaosTypeName = formData.get("chaosType");
        ChaosType chaosType;
        try {
            chaosType = ChaosType.valueOf(chaosTypeName);
        } catch (IllegalArgumentException | NullPointerException e) {
            return HttpResponse.badRequest(error("Unknown chaosType '" + chaosTypeName + "'"));
        }

        try {
            chaosType.addChaos(formData, key);
        } catch (IllegalArgumentException | NullPointerException e) {
            return HttpResponse.badRequest(error("Invalid chaos configuration: " + e.getMessage()));
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Chaos Added for " + key + " data " + formData);

        return HttpResponse.ok(response);
    }

    @Delete("/removeChaos/{key}/{chaosId}")
    public HttpResponse<Map<String, String>> removeChaos(@PathVariable String key, @PathVariable String chaosId) {

        Store.INSTANCE.remove(key, chaosId);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Removed Chaos for " + key);

        return HttpResponse.ok(response);
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    @Get("/metrics")
    public HttpResponse<Map<String, Object>> metrics() {
        List<Map<String, Object>> data = new ArrayList<>();
        Map<String, Map<String, Object>> hosts = new LinkedHashMap<>();

        long connections = 0;
        long activeConnections = 0;
        long failedConnections = 0;
        long upstreamBytes = 0;
        long downstreamBytes = 0;
        long droppedChunks = 0;
        long chaosProfiles = 0;

        for (String key : Store.INSTANCE.keys()) {
            Map<String, Object> row = metricsFor(key);
            data.add(row);

            connections += (Long) row.get("connections");
            activeConnections += (Long) row.get("activeConnections");
            failedConnections += (Long) row.get("failedConnections");
            upstreamBytes += (Long) row.get("upstreamBytes");
            downstreamBytes += (Long) row.get("downstreamBytes");
            droppedChunks += (Long) row.get("droppedChunks");
            chaosProfiles += (Long) row.get("chaosCount");

            String host = String.valueOf(row.get("serverHost"));
            Map<String, Object> hostRow = hosts.computeIfAbsent(host, h -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("host", h);
                m.put("proxies", 0L);
                m.put("connections", 0L);
                m.put("activeConnections", 0L);
                m.put("upstreamBytes", 0L);
                m.put("downstreamBytes", 0L);
                m.put("droppedChunks", 0L);
                m.put("chaosProfiles", 0L);
                return m;
            });
            hostRow.put("proxies", (Long) hostRow.get("proxies") + 1);
            hostRow.put("connections", (Long) hostRow.get("connections") + (Long) row.get("connections"));
            hostRow.put("activeConnections", (Long) hostRow.get("activeConnections") + (Long) row.get("activeConnections"));
            hostRow.put("upstreamBytes", (Long) hostRow.get("upstreamBytes") + (Long) row.get("upstreamBytes"));
            hostRow.put("downstreamBytes", (Long) hostRow.get("downstreamBytes") + (Long) row.get("downstreamBytes"));
            hostRow.put("droppedChunks", (Long) hostRow.get("droppedChunks") + (Long) row.get("droppedChunks"));
            hostRow.put("chaosProfiles", (Long) hostRow.get("chaosProfiles") + (Long) row.get("chaosCount"));
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("proxies", (long) data.size());
        totals.put("connections", connections);
        totals.put("activeConnections", activeConnections);
        totals.put("failedConnections", failedConnections);
        totals.put("upstreamBytes", upstreamBytes);
        totals.put("downstreamBytes", downstreamBytes);
        totals.put("droppedChunks", droppedChunks);
        totals.put("chaosProfiles", chaosProfiles);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totals", totals);
        response.put("hosts", new ArrayList<>(hosts.values()));
        response.put("data", data);

        return HttpResponse.ok(response);
    }

    @Get("/metrics/{key}")
    public HttpResponse<Map<String, Object>> metricsForKey(@PathVariable String key) {
        if (Store.INSTANCE.getServer(key) == null) {
            return HttpResponse.notFound(errorMap("No proxy found for key " + key));
        }
        return HttpResponse.ok(metricsFor(key));
    }

    @Get(value = "/prometheus", produces = MediaType.TEXT_PLAIN)
    public HttpResponse<String> prometheus() {
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP chaosbridge_proxy_active Whether the proxy is running (1) or not (0)\n");
        sb.append("# TYPE chaosbridge_proxy_active gauge\n");
        sb.append("# HELP chaosbridge_connections_total Total accepted client connections\n");
        sb.append("# TYPE chaosbridge_connections_total counter\n");
        sb.append("# HELP chaosbridge_connections_active Currently open client connections\n");
        sb.append("# TYPE chaosbridge_connections_active gauge\n");
        sb.append("# HELP chaosbridge_connections_failed_total Connections that terminated with an error\n");
        sb.append("# TYPE chaosbridge_connections_failed_total counter\n");
        sb.append("# HELP chaosbridge_upstream_bytes_total Bytes forwarded from client to target\n");
        sb.append("# TYPE chaosbridge_upstream_bytes_total counter\n");
        sb.append("# HELP chaosbridge_downstream_bytes_total Bytes forwarded from target to client\n");
        sb.append("# TYPE chaosbridge_downstream_bytes_total counter\n");
        sb.append("# HELP chaosbridge_dropped_chunks_total Chunks dropped by packet loss chaos\n");
        sb.append("# TYPE chaosbridge_dropped_chunks_total counter\n");
        sb.append("# HELP chaosbridge_latency_chunks_total Chunks delayed by latency chaos\n");
        sb.append("# TYPE chaosbridge_latency_chunks_total counter\n");
        sb.append("# HELP chaosbridge_throttled_chunks_total Chunks throttled by bandwidth chaos\n");
        sb.append("# TYPE chaosbridge_throttled_chunks_total counter\n");
        sb.append("# HELP chaosbridge_chaos_profiles Attached chaos profiles\n");
        sb.append("# TYPE chaosbridge_chaos_profiles gauge\n");

        for (String key : Store.INSTANCE.keys()) {
            Map<String, Object> row = metricsFor(key);
            String labels = "proxy=\"" + escapeLabel(String.valueOf(row.get("key")))
                    + "\",host=\"" + escapeLabel(String.valueOf(row.get("serverHost")))
                    + "\",port=\"" + escapeLabel(String.valueOf(row.get("serverPort"))) + "\"";
            sb.append("chaosbridge_proxy_active{").append(labels).append("} ")
                    .append(Boolean.TRUE.equals(row.get("active")) ? 1 : 0).append('\n');
            appendMetric(sb, "chaosbridge_connections_total", labels, row.get("connections"));
            appendMetric(sb, "chaosbridge_connections_active", labels, row.get("activeConnections"));
            appendMetric(sb, "chaosbridge_connections_failed_total", labels, row.get("failedConnections"));
            appendMetric(sb, "chaosbridge_upstream_bytes_total", labels, row.get("upstreamBytes"));
            appendMetric(sb, "chaosbridge_downstream_bytes_total", labels, row.get("downstreamBytes"));
            appendMetric(sb, "chaosbridge_dropped_chunks_total", labels, row.get("droppedChunks"));
            appendMetric(sb, "chaosbridge_latency_chunks_total", labels, row.get("latencyChunks"));
            appendMetric(sb, "chaosbridge_throttled_chunks_total", labels, row.get("throttledChunks"));
            appendMetric(sb, "chaosbridge_chaos_profiles", labels, row.get("chaosCount"));
        }

        return HttpResponse.ok(sb.toString()).contentType(MediaType.TEXT_PLAIN);
    }

    private Map<String, Object> metricsFor(String key) {
        ProxyMetrics metrics = MetricsRegistry.INSTANCE.register(key);
        String[] parts = splitKey(key);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("port", parts == null ? null : parts[0]);
        row.put("serverHost", parts == null ? key : parts[1]);
        row.put("serverPort", parts == null ? null : parts[2]);
        row.put("active", Store.INSTANCE.getServer(key) != null);
        row.put("connections", metrics.getConnections());
        row.put("activeConnections", metrics.getActiveConnections());
        row.put("failedConnections", metrics.getFailedConnections());
        row.put("upstreamBytes", metrics.getUpstreamBytes());
        row.put("downstreamBytes", metrics.getDownstreamBytes());
        row.put("droppedChunks", metrics.getDroppedChunks());
        row.put("latencyChunks", metrics.getLatencyChunks());
        row.put("throttledChunks", metrics.getThrottledChunks());
        row.put("uptimeMs", metrics.getUptimeMs());
        List<Map<String, Object>> chaos = Store.INSTANCE.getChaosList(key);
        row.put("chaosCount", (long) chaos.size());
        row.put("chaos", chaos);
        return row;
    }

    private static void appendMetric(StringBuilder sb, String name, String labels, Object value) {
        sb.append(name).append('{').append(labels).append("} ").append(value).append('\n');
    }

    private static String escapeLabel(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String[] splitKey(String key) {
        if (key == null) {
            return null;
        }
        int firstColon = key.indexOf(':');
        int lastColon = key.lastIndexOf(':');
        if (firstColon <= 0 || lastColon <= firstColon) {
            return null;
        }
        return new String[]{
                key.substring(0, firstColon),
                key.substring(firstColon + 1, lastColon),
                key.substring(lastColon + 1)
        };
    }

    private static Map<String, String> error(String message) {
        Map<String, String> response = new HashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }

    private static Map<String, Object> errorMap(String message) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "error");
        response.put("message", message);
        return response;
    }
}
