package com.github.sahariardev.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.util.CharsetUtil;

public class ByteBuffUtil {
    public static void printByteBuf(ByteBuf byteBuf) {
        if (byteBuf == null) {
            System.out.println("ByteBuf is null");
            return;
        }

        // Duplicate to avoid modifying the original buffer
        ByteBuf copy = byteBuf.duplicate();

        // Readable bytes
        int readableBytes = copy.readableBytes();
        System.out.println("ByteBuf Content (" + readableBytes + " bytes):");

        // Convert to String (Assuming UTF-8 text)
        String content = copy.toString(CharsetUtil.UTF_8);
        System.out.println(content);

    }
}
