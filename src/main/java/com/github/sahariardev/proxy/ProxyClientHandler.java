package com.github.sahariardev.proxy;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;

import java.util.function.Consumer;

@ChannelHandler.Sharable
public class ProxyClientHandler extends ChannelInboundHandlerAdapter {

    private final Consumer<Object> consumer;

    public ProxyClientHandler(Consumer<Object> consumer) {
        this.consumer = consumer;
    }

    @Override
    public void channelRead(ChannelHandlerContext channelHandlerContext, Object msg) throws Exception {
        System.out.println("Got response");
        consumer.accept(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}
