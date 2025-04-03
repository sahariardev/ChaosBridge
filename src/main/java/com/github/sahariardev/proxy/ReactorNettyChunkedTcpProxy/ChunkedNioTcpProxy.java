package com.github.sahariardev.proxy.ReactorNettyChunkedTcpProxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ChunkedNioTcpProxy {

    private static final int PROXY_PORT = 1081;  // Proxy listens here
    private static final String TARGET_HOST = "aggdb.therapbd.net"; // Target server
    private static final int TARGET_PORT = 1521;

    private static final int CHUNK_SIZE = 4 * 1024;  // Chunk size in bytes
    private static final int CHUNK_DELAY_MS = 100;  // Delay between chunks

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public static void main(String[] args) throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(PROXY_PORT));
        serverChannel.configureBlocking(false);
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("NIO Chunked TCP Proxy running on port " + PROXY_PORT);

        while (true) {
            selector.select(); // Wait for events
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                try {
                    if (key.isAcceptable()) {
                        acceptClient(selector, serverChannel);
                    } else if (key.isReadable()) {
                        key.interestOps(0); // Pause reading while processing
                        handleRead(key);
                    }
                } catch (Exception ignore) {
                    System.out.println(ignore);
                }

            }
        }
    }

    private static void acceptClient(Selector selector, ServerSocketChannel serverChannel) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        SocketChannel targetChannel = SocketChannel.open(new InetSocketAddress(TARGET_HOST, TARGET_PORT));
        targetChannel.configureBlocking(false);

        clientChannel.register(selector, SelectionKey.OP_READ, targetChannel);
        targetChannel.register(selector, SelectionKey.OP_READ, clientChannel);
    }

    private static void handleRead(SelectionKey key) throws IOException {
        SocketChannel sourceChannel = (SocketChannel) key.channel();
        SocketChannel destChannel = (SocketChannel) key.attachment();

        System.out.println(sourceChannel.getRemoteAddress() + " --> " + destChannel.getRemoteAddress());

        ByteBuffer buffer = ByteBuffer.allocate(CHUNK_SIZE * 4); // Larger read buffer
        int bytesRead = sourceChannel.read(buffer);

        if (bytesRead == -1) {
            closeChannel(sourceChannel);
            closeChannel(destChannel);
            key.cancel();
            return;
        }

        buffer.flip();
        startChunkedWrite(key, destChannel, buffer, buffer.position());
    }

    private static void startChunkedWrite(SelectionKey key,
                                          SocketChannel destChannel,
                                          ByteBuffer fullBuffer,
                                          int chunkStartingPosition) throws IOException {
        // Create a view buffer for safe chunking
        ByteBuffer chunk = fullBuffer.duplicate();
        chunk.position(chunkStartingPosition);
        chunk.limit(Math.min(fullBuffer.position() + CHUNK_SIZE, fullBuffer.limit()));

        scheduler.schedule(() -> {
            try {
                int bytesWritten = destChannel.write(chunk);

                if (chunk.hasRemaining()) {
                    fullBuffer.position(bytesWritten);
                    startChunkedWrite(key, destChannel, fullBuffer, chunk.position());
                } else if (fullBuffer.hasRemaining()) {

                    fullBuffer.position(chunk.limit());
                    startChunkedWrite(key, destChannel, fullBuffer, fullBuffer.position());
                } else {
                    key.interestOps(SelectionKey.OP_READ); // Re-enable reads
                }
            } catch (IOException e) {
                System.out.println("[CHUNK ERROR] " + e.getMessage());
                closeConnection(key, (SocketChannel) key.channel(), destChannel);
            }
        }, CHUNK_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private static void closeConnection(SelectionKey key, SocketChannel channel, SocketChannel destChannel) {
        if (key != null) {
            key.cancel();
            try {
                key.channel().close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        closeChannel(channel);
        closeChannel(destChannel);
    }

    private static void closeChannel(SocketChannel channel) {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (IOException ignored) {
        }
    }
}

