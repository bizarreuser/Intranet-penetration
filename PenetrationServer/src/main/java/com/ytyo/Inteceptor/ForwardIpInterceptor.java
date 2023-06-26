package com.ytyo.Inteceptor;

import io.netty.channel.ChannelHandlerContext;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ForwardIpInterceptor {
    public static ConcurrentHashMap<String, AtomicInteger> map = new ConcurrentHashMap<>();


    public static void addCount(String ip) {
        AtomicInteger count = map.get(ip);
        if (count != null) {
            count.incrementAndGet();
        } else {
            map.put(ip, new AtomicInteger());
        }
    }

    public boolean intercept(ChannelHandlerContext context) {
        AtomicInteger count = map.get(context.channel().remoteAddress().toString());
        if (count != null) {
            return count.get() > 10;
        }
        return false;
    }

    public static void passIp(String ip) {
        AtomicInteger count = map.get(ip);
        if (count != null) {
            count.set(0);
        }
    }
}
