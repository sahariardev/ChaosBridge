package com.github.sahariardev.proxy.chaos;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;

@ChannelHandler.Sharable
public class BandwidthLimitHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("Writing Response");
//        if (msg instanceof ByteBuf) {
//            sendChunks(ctx, (ByteBuf) msg);
//        } else {
//            ctx.writeAndFlush(msg);
//        }
        ctx.writeAndFlush(msg);
    }

    private void sendChunks(ChannelHandlerContext ctx, ByteBuf msg) {
        int chunkSize = 1000;
        if (msg.readableBytes() > 0) {
            System.out.println("sending in chunks");
            int currentChunkSize = Math.min(msg.readableBytes(), chunkSize);
            ByteBuf chunk = msg.readRetainedSlice(currentChunkSize);
            ChannelFuture writeFuture = ctx.writeAndFlush(chunk);

            writeFuture.addListener(future -> {
                if (future.isSuccess()) {
                    System.out.println("Sending " + currentChunkSize + " chunks");
                }
            });

            if(msg.readableBytes() > 0) {
                ctx.executor().schedule(() -> sendChunks(ctx, msg), 1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        }
    }
}
