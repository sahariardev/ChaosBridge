package com.github.sahariardev.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.sahariardev.common.Constant;
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

@Controller("/")
public class UiController {

    @Inject
    @Named("virtual-thread-executor")
    private ExecutorService executorService;

    private static final Logger logger = LoggerFactory.getLogger(UiController.class);

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

    @Delete("/proxy/{key}")
    public HttpResponse<?> deleteProxy(@PathVariable String key) {
        Server server = Store.INSTANCE.getServer(key);

        if (server != null) {
            server.stop();
        }

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Stopped Server " + key + " data ");

        return HttpResponse.ok(response);
    }

    @Post("/addChaos/{key}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<Map<String, String>> applyChaos(@PathVariable String key, @Body Map<String, String> formData) {
        int bytePerSecond = Integer.parseInt(formData.get("bytePerSecond"));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put(Constant.TYPE, "bandwidthChaos");
        json.put("bytePerSecond", bytePerSecond);
        json.put(Constant.LINE, Constant.DOWNSTREAM);

        Store.INSTANCE.put(key, json);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Chaos Added for " + key + " data " + formData);

        return HttpResponse.ok(response);
    }
}
