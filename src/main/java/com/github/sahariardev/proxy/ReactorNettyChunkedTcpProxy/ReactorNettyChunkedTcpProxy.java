package com.github.sahariardev.proxy.ReactorNettyChunkedTcpProxy;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;
import reactor.netty.tcp.TcpServer;

import java.time.Duration;

public class ReactorNettyChunkedTcpProxy {

    private static final int CHUNK_SIZE = 1024; // Chunk size in bytes
    private static final Duration CHUNK_DELAY = Duration.ofMillis(100); // Delay between chunks

    public static void main(String[] args) {
        int proxyPort = 9000; // Port for incoming connections
        String targetHost = "httpforever.com"; // Target backend
        int targetPort = 80; // Target server port

        TcpServer.create()
                .port(proxyPort)
                .handle((inbound, outbound) ->
                        TcpClient.create()
                                .host(targetHost)
                                .port(targetPort)
                                .connect()
                                .flatMap(remote -> forwardTrafficWithChunking((Connection) inbound, (Connection) outbound, remote))
                )
                .bindNow()
                .onDispose()
                .block();
    }

    private static Mono<Void> forwardTrafficWithChunking(Connection inbound, Connection outbound, Connection remote) {
        return Mono.when(
                chunkedForward(inbound.inbound().receive().asByteArray(), remote.outbound()),
                chunkedForward(remote.inbound().receive().asByteArray(), outbound.outbound())
        ).then();
    }

    private static Mono<Void> chunkedForward(Flux<byte[]> data, reactor.netty.NettyOutbound targetOutbound) {
        return data
                .concatMap(bytes -> Flux.just(bytes)
                        .delayElements(CHUNK_DELAY) // Add delay per chunk
                )
                .flatMap(chunk -> targetOutbound.sendByteArray(Mono.just(limitChunk(chunk))))
                .then();
    }

    private static byte[] limitChunk(byte[] data) {
        if (data.length <= CHUNK_SIZE) {
            return data;
        }
        byte[] chunk = new byte[CHUNK_SIZE];
        System.arraycopy(data, 0, chunk, 0, CHUNK_SIZE);
        return chunk;
    }
}
