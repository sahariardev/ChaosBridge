package com.github.sahariardev;

import com.github.sahariardev.proxy.Server;

public class Main {
    public static void main(String[] args) {
        Server server = new Server();
        server.start(1081, "localhost", 3000);
    }
}