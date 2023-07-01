package com.ytyo.Utils;

import io.netty.channel.Channel;


public class ConnectUtil {

    public static boolean connectActive(Channel channel) {
        return channel.isActive() && channel.isWritable();
    }

    public static boolean connectInActive(Channel channel) {
        return !connectActive(channel);
    }
}
