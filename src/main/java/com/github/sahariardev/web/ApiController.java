package com.github.sahariardev.web;

import com.github.sahariardev.chaos.ChaosConfig;
import com.github.sahariardev.chaos.ChaosType;
import com.github.sahariardev.common.Store;
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
            Map<String, String> map = new HashMap<>();
            String[] split = key.split(":");
            map.put("port", split[0]);
            map.put("serverHost", split[1]);
            map.put("serverPort", split[2]);
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

        String key = String.format("%s:%s:%s", formData.get("port"), formData.get("serverHost"), formData.get("serverPort"));
        Server server = new Server(Integer.parseInt(formData.get("port")),
                formData.get("serverHost"),
                Integer.parseInt(formData.get("serverPort")), key);

        Store.INSTANCE.addServer(key, server);

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
        ChaosType chaosType = ChaosType.valueOf(formData.get("chaosType"));
        chaosType.addChaos(formData, key);

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
}
