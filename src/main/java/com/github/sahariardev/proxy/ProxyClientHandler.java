package com.github.sahariardev.proxy;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.function.Consumer;
import java.util.function.Supplier;

@ChannelHandler.Sharable
public class ProxyClientHandler extends ChannelInboundHandlerAdapter {

    private final Consumer<Object> consumer;

    public ProxyClientHandler(Consumer<Object> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
       consumer.accept(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
