package com.ytyo.Dispatcher.Message;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.util.Optional;

/**
 * code  1.关闭帧 2.开启帧 3.消息帧 ...
 */

public record Frame(FrameCode code, ByteBuf content) {

    /**
     * 不会 release Frame内部的buf
     *
     * @return
     */
    public ByteBuf toBuf() {
        ByteBuf heapBuffer = ByteBufAllocator.DEFAULT.heapBuffer();
        heapBuffer.writeShort(code.code());
        if (content != null) {
            heapBuffer.writeBytes(content);
        } else {
            throw new RuntimeException("content不应该为空");
        }
        return heapBuffer;
    }

    public static Optional<Frame> from(ByteBuf buf) {
        if (buf == null)
            return Optional.empty();
        short code = buf.readShort();
        ByteBuf content = ByteBufAllocator.DEFAULT.heapBuffer(buf.readableBytes());
        buf.readBytes(content);
        Optional<FrameCode> frameCode = FrameCode.from(code);
        return frameCode.map(value -> new Frame(value, content));
    }

    public void release() {
        if (content != null)
            content.release();
    }

    @Override
    public String toString() {
        return "Frame{" +
                "code=" + code +
                ", content=" + content +
                '}';
    }
}
