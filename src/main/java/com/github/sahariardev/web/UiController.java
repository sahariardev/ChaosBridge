package com.github.sahariardev.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.JSONPObject;
import com.github.sahariardev.common.Store;
import com.github.sahariardev.proxy.Server;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.views.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Controller("/")
public class UiController {

    private static final Logger logger = LoggerFactory.getLogger(UiController.class);

    @View("form")
    @Get("/createNewProxy")
    public HttpResponse<?> index() {
        logger.info("[Get] Creating new proxy");
        return HttpResponse.ok();
    }

    @Post("/createNewProxy")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<?> render(@Body Map<String, String> formData) throws IOException {
        logger.info("[POST] Creating new proxy with data {}", formData);

        Server server = new Server();

        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        executorService.execute(() -> {
            try {
                logger.info("starting new proxy with data {}", formData);
                server.start(Integer.parseInt(formData.get("port")),
                        formData.get("serverHost"),
                        Integer.parseInt(formData.get("serverPort")));
            } catch (IOException e) {
                logger.error("Error starting new proxy", e);
                throw new RuntimeException(e);
            }
        });

        String key = String.format("%s-%s-%s", formData.get("port"), formData.get("serverHost"), formData.get("serverPort"));

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("key", key);
        response.put("message", "Proxy started successfully " + formData);

        return HttpResponse.ok(response);
    }


    @Post("/addChaos/{key}")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public HttpResponse<Map<String, String>> applyChaos(@PathVariable String key, @Body Map<String, String> formData) {
        int bytePerSecond = Integer.parseInt(formData.get("bytePerSecond"));

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode json = mapper.createObjectNode();
        json.put("type", "bandwidthChaos");
        json.put("bytePerSecond", bytePerSecond);

        Store.INSTANCE.put(key, json);

        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Chaos Added for " + key + " data " + formData);

        return HttpResponse.ok(response);
    }
}
