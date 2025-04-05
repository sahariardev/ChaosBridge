package com.github.sahariardev.common;

import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private ConcurrentHashMap<String, String> map = new ConcurrentHashMap();
    public static final Store INSTANCE = new Store();
}
