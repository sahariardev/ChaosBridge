package com.github.sahariardev.proxy.ReactorNettyChunkedTcpProxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;

import java.util.concurrent.TimeUnit;

@ChannelHandler.Sharable
public class BandwidthLimitHandler extends ChannelInboundHandlerAdapter {
    private static final int CHUNK_SIZE = 1024; // 1KB chunks
    private static final int DELAY_MS = 100;    // 100ms between chunks

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ByteBuf)) {
            ctx.fireChannelRead(msg); // Pass through non-ByteBuf messages
            return;
        }

        ByteBuf data = (ByteBuf) msg;
        if (!data.isReadable()) {
            data.release();
            return;
        }

        // Create a duplicate for chunking (doesn't modify reader index)
        ByteBuf chunkingBuffer = data.retainedDuplicate();
        data.release(); // Release the original buffer

        scheduleNextChunk(ctx, chunkingBuffer, 0);
    }

    private void scheduleNextChunk(ChannelHandlerContext ctx, ByteBuf buffer, int offset) {
        if (!ctx.channel().isActive()) {
            buffer.release();
            return;
        }

        int remaining = buffer.readableBytes() - offset;
        if (remaining <= 0) {
            buffer.release();
            return;
        }

        int chunkSize = Math.min(CHUNK_SIZE, remaining);
        final ByteBuf chunk = buffer.retainedSlice(offset, chunkSize);

        ctx.executor().schedule(() -> {
            ctx.writeAndFlush(chunk).addListener(future -> {
                chunk.release(); // Release the chunk after writing

                if (future.isSuccess()) {
                    scheduleNextChunk(ctx, buffer, offset + chunkSize);
                } else {
                    buffer.release();
                    ctx.close();
                }
            });
        }, DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("Bandwidth limiting error: " + cause.getMessage());
        ctx.close();
    }
}