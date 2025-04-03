package com.github.sahariardev.proxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@ChannelHandler.Sharable
public class ProxyServerHandler extends ChannelInboundHandlerAdapter {

    public static final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(1);

    private final String host;

    private final int port;

    private final Queue<Object> queue = new ConcurrentLinkedQueue<>();

    private Channel channel;

    public ProxyServerHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        createClientChannel(ctx);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        System.out.println("Got message");
        createClientChannel(ctx);
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(msg);
        } else {
            queue.add(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    private void createClientChannel(ChannelHandlerContext ctx) {
        if (channel != null && channel.isActive()) {
            return;
        }

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(eventLoopGroup)
                .channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(new ProxyClientHandler(ctx::fireChannelRead));
                    }
                });
        bootstrap.connect(host, port).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {

                this.channel = future.channel();

                while (!queue.isEmpty()) {
                    future.channel().writeAndFlush(queue.poll());
                }

            } else {
                future.cause().printStackTrace();
            }
        });
    }
}
