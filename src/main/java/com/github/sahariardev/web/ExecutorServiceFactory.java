package com.github.sahariardev.web;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Factory
public class ExecutorServiceFactory {

    @Bean
    @Named("virtual-thread-executor")
    public ExecutorService virtualThreadExecutor() {
        System.out.println("this is triggered");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
