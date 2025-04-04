package com.github.sahariardev;

import com.github.sahariardev.proxy.Server;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.start(1081, "httpforever.com", 80);
    }
}