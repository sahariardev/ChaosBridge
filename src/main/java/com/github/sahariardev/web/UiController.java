package com.github.sahariardev.web;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.views.View;

import java.util.Map;

@Controller("/")
public class UiController {
    @View("form") // uses views/form.vm
    @Get("/")
    public HttpResponse<?> index() {
        return HttpResponse.ok();
    }

    @Post("/submit")
    public HttpResponse<String> applyChaos(@Body Map<String, String> formData) {
        String host = formData.get("host");
        int latency = Integer.parseInt(formData.get("latency"));
        int bandwidth = Integer.parseInt(formData.get("bandwidth"));

        // You can wire this to your chaos proxy logic
        System.out.printf("Applying chaos to host %s with latency=%dms, bandwidth=%dB/s%n", host, latency, bandwidth);

        return HttpResponse.ok("Chaos applied successfully!");
    }
}
