package com.ytyo.Utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

public class BufUtil {
    public static ByteBuf readCopy(ByteBuf buf) {
        ByteBuf buffer = ByteBufAllocator.DEFAULT.heapBuffer();
        buffer.writeBytes(buf);
        return buffer;
    }
}
