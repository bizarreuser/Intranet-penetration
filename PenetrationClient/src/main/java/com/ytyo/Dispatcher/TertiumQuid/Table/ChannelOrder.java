package com.ytyo.Dispatcher.TertiumQuid.Table;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;

// 存放 uuid 和 channel对应关系的表
public class ChannelOrder {
    private final static ReentrantReadWriteLock.WriteLock writeLock;
    private final static ReentrantReadWriteLock.ReadLock readLock;
    private final static ConcurrentHashMap<String, ChannelEntry> keyValueMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<ChannelEntry, String> valueKeyMap = new ConcurrentHashMap<>();

    static {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        writeLock = lock.writeLock();
        readLock = lock.readLock();
    }

    // 插入键值对
    public static void put(String uuid, Channel channel) {
        AtomicBoolean containChannel = new AtomicBoolean(false);
        writeLock.lock();
        try {
            valueKeyMap.forEach((k, v) -> {
                if (k.getChannel() == channel) {
                    containChannel.set(true);
                }
            });
            if (keyValueMap.containsKey(uuid) || containChannel.get()) {
                throw new IllegalArgumentException("Key or value already exists.");
            }
            ChannelEntry channelEntry = new ChannelEntry(channel);
            keyValueMap.put(uuid, channelEntry);
            valueKeyMap.put(channelEntry, uuid);
        } finally {
            writeLock.unlock();
        }
    }


    // 通过键获取值
    public static Optional<Channel> getChannel(String uuid) {
        readLock.lock();
        try {
            ChannelEntry entry = keyValueMap.get(uuid);
            if (entry == null) {
                return Optional.empty();
            }
            return Optional.of(entry.getChannel());
        } finally {
            readLock.unlock();
        }
    }

    public static Optional<ChannelEntry> getChannelEntry(String uuid) {
        readLock.lock();
        try {
            ChannelEntry entry = keyValueMap.get(uuid);
            return Optional.ofNullable(entry);
        } finally {
            readLock.unlock();
        }
    }


    // 通过值获取键
    public static Optional<String> getUUID(Channel channel) {
        AtomicReference<String> uuid = new AtomicReference<>(null);
        readLock.lock();
        valueKeyMap.forEach((k, v) -> {
            if (k.getChannel() == channel) {
                uuid.set(v);
            }
        });
        readLock.unlock();

        return Optional.ofNullable(uuid.get());
    }

    // 删除键值对,只能通过channel删除;
    public static Optional<String> removeByChannel(Channel channel) {

        AtomicReference<String> key = new AtomicReference<>(null);
        AtomicReference<ChannelEntry> entry = new AtomicReference<>(null);
        writeLock.lock();
        try {
            valueKeyMap.forEach((k, v) -> {
                if (k.getChannel() == channel) {
                    key.set(v);
                    entry.set(k);
                }
            });
            if (key.get() != null) {// 说明channel存在
                keyValueMap.remove(key.get());
                valueKeyMap.remove(entry.get());
                return Optional.ofNullable(key.get());
            } else {
                return Optional.empty();
            }
        } finally {
            writeLock.unlock();
        }


    }

    // 判断键是否存在
    public static boolean containsUUID(String uuid) {
        readLock.lock();
        boolean containsKey = keyValueMap.containsKey(uuid);
        readLock.unlock();
        return containsKey;
    }

    // 判断值是否存在
    public static boolean containsChannelEntry(ChannelEntry channelEntry) {
        readLock.lock();
        boolean containsKey = valueKeyMap.containsKey(channelEntry);
        readLock.unlock();
        return containsKey;
    }


    /**
     * 向本地服务器请求收到的数据
     *
     * @param uuid  channel对应的uuid
     * @param order 响应消息的序号
     * @param buf   响应内容
     */
    public static boolean reqByOrder(String uuid, int order, ByteBuf buf) {

        writeLock.lock();
        buf.retain();
        try {
            ChannelEntry channelEntry = keyValueMap.get(uuid);
            if (channelEntry == null) {
                System.out.printf("uuid:%s channel 未找到\n", uuid);
                buf.release();
                return false;
            }
            System.out.printf("客户端请求本地服务器，uuid:%s,消息序号:%s,消息:%s\n", uuid, order, buf.toString(StandardCharsets.UTF_8));
            buf.resetReaderIndex();
            return channelEntry.bufferWrite(order, buf);
        } finally {
            writeLock.unlock();
        }

    }


    public static boolean hasChannel(Channel channel) {
        AtomicBoolean have = new AtomicBoolean(false);
        readLock.lock();
        valueKeyMap.forEach((k, v) -> {
            if (k.getChannel() == channel) {
                have.set(true);
            }
        });
        readLock.unlock();
        return have.get();
    }


    public static class ChannelEntry {
        //对应外界连接
        private final Channel channel;
        private int next;//为响应准备的变量
        private final Map<Integer, ByteBuf> bufMap = new ConcurrentHashMap<>();

        public ChannelEntry(Channel channel) {
            this.channel = channel;
        }

        //保证了一个映射channel上的消息按顺序发送，buf被消耗
        public boolean bufferWrite(int order, ByteBuf buf) {
            if (order == next) {
                buf.resetReaderIndex();
                channel.writeAndFlush(buf);
                bufMap.remove(order);
                next++;
                if (bufMap.containsKey(next)) {
                    return bufferWrite(next, bufMap.get(next));
                }
                return true;
            } else if (order > next) {
                bufMap.put(order, buf);
                return true;
            } else {
                System.out.printf("已请求过的消息序号:%s,消息:%s\n", order, buf.toString(StandardCharsets.UTF_8));
                buf.release();
                return false;
            }
        }

        public Channel getChannel() {
            return channel;
        }


    }

}
