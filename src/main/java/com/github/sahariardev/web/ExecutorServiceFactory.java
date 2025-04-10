package com.github.sahariardev.web;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Singleton
public class ExecutorServiceFactory {

    @Singleton
    @Named("virtual-thread-executor")
    public ExecutorService virtualThreadExecutor() {
        System.out.println("this is triggered");
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
