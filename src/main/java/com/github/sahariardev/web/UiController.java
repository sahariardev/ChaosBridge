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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

        String key = String.format("%s-%s-%s", formData.get("port"), formData.get("serverHost"), formData.get("serverPort"));
        Server server = new Server(Integer.parseInt(formData.get("port")),
                formData.get("serverHost"),
                Integer.parseInt(formData.get("serverPort")), key);

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


    @View("addChaos")
    @Get("/addChaos/{key}")
    public HttpResponse<?> addChaos(@PathVariable String key) {
        logger.info("[Get] Add Chaos {}", key);
        return HttpResponse.ok();
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
