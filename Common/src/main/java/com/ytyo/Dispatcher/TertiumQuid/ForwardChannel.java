package com.ytyo.Dispatcher.TertiumQuid;

import com.ytyo.Dispatcher.Message.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

/**
 * @param channel s2与c2的一个连接
 */
public record ForwardChannel(Channel channel) {

    /**
     *  注意: 该方法不会release buf
     */
    public void writeAndFlush(ByteBuf buf) {
        buf.retain();
        channel.writeAndFlush(buf);
    }

    public void writeForceCloseFrame(String uuid) {
        channel.writeAndFlush(Protocol.forceCloseFrame(uuid));
    }

    public void writeCloseFrame(String uuid, int msgOrder) {
        channel.writeAndFlush(Protocol.closeFrame(uuid, msgOrder));
    }

    public void writeOpenFrame(String uuid) {
        channel.writeAndFlush(Protocol.openFrame(uuid));
    }

    public void writeOpenAckFrame(String uuid) {
        channel.writeAndFlush(Protocol.openAckFrame(uuid));
    }

    public void writeCloseAckFrame(String uuid) {
        channel.writeAndFlush(Protocol.closeAckFrame(uuid));
    }
}
