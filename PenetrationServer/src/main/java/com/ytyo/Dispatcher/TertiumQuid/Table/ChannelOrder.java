package com.ytyo.Dispatcher.TertiumQuid.Table;

import com.ytyo.Controller.LogController;
import com.ytyo.Utils.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;


//使用的时候，一个端口，使用 同一张表
public class ChannelOrder {
    private final ReentrantReadWriteLock.WriteLock writeLock;
    private final ReentrantReadWriteLock.ReadLock readLock;
    public final ConcurrentHashMap<String, ChannelEntry> keyValueMap = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<ChannelEntry, String> valueKeyMap = new ConcurrentHashMap<>();

    {
        LogController.tables.add(this);
    }

    public ChannelOrder() {
        ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        writeLock = lock.writeLock();
        readLock = lock.readLock();
    }


    // 插入键值对
    public boolean put(String uuid, Channel channel) {

        AtomicBoolean containChannel = new AtomicBoolean(false);
        writeLock.lock();
        try {
            valueKeyMap.forEach((k, v) -> {
                if (k.getChannel() == channel) {
                    containChannel.set(true);
                }
            });
            if (keyValueMap.containsKey(uuid) || containChannel.get()) {
                return false;
            }
            ChannelEntry channelEntry = new ChannelEntry(channel);
            keyValueMap.put(uuid, channelEntry);
            valueKeyMap.put(channelEntry, uuid);
            return true;
        } finally {
            writeLock.unlock();
        }
    }

    // 通过键获取值
    public Optional<Channel> getChannel(String uuid) {
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

    // 通过值获取键
    public Optional<String> getUUID(Channel channel) {
        AtomicReference<String> uuid = new AtomicReference<>(null);
        readLock.lock();
        valueKeyMap.forEach((k, v) -> {
            if (k.getChannel() == channel) {
                uuid.set(v);
            }
        });
        readLock.unlock();
        if (uuid.get() == null) {
            return Optional.empty();
        }
        return Optional.of(uuid.get());
    }

    // 删除键值对,只能通过channel删除
    public Optional<String> removeByChannel(Channel channel) {
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
                return Optional.of(key.get());
            } else {
                return Optional.empty();
            }
        } finally {
            writeLock.unlock();
        }


    }

    // 判断键是否存在
    public boolean containsUUID(String uuid) {
        readLock.lock();
        boolean containsKey = keyValueMap.containsKey(uuid);
        readLock.unlock();
        return containsKey;
    }

    // 判断值是否存在
    public boolean containsChannelEntry(ChannelEntry channelEntry) {
        readLock.lock();
        boolean containsKey = valueKeyMap.containsKey(channelEntry);
        readLock.unlock();
        return containsKey;
    }



    /**
     *
     * @param uuid  channel对应的uuid
     * @param order 响应消息的order
     * @param buf   响应内容
     */
    public boolean respByOrder(String uuid, int order, ByteBuf buf) {

        writeLock.lock();
        try {
            ChannelEntry channelEntry = keyValueMap.get(uuid);
            if (channelEntry == null) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.getBytes(0, bytes);
                LogController.errors.add(String.format("序号为%s的channel没有找到响应连接,消息序号为%s.消息长度为%s", uuid, order, buf.readableBytes()));
                System.out.println("没有找到序号为" + uuid + "的对应响应连接");
                return false;
            }

            System.out.println("服务方收到uuid为" + uuid + "消息序号为" + order + "的响应消息,发送给channel:" + channelEntry.channel);
            //由于WriteEvent copy了buf，所以这个buf只会读不会release
            return channelEntry.run(order, Event.WriteEvent(buf));
        } finally {
            writeLock.unlock();
        }

    }

    public boolean closeByOrder(String uuid, int order) {
        writeLock.lock();
        try {
            ChannelEntry channelEntry = keyValueMap.get(uuid);
            if (channelEntry == null) {
                System.out.printf("序号为%s的关闭帧没有找到要关闭的编号为%s的响应连接%n", order, uuid);
                return true;
            }
            System.out.println("服务方收到uuid为" + uuid + "消息序号为" + order + "的关闭帧,关闭channel:" + channelEntry.channel);
            return channelEntry.run(order, Event.CloseEvent());
        } finally {
            writeLock.unlock();
        }
    }


    public boolean hasChannel(Channel channel) {
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

    public static class Event {

        private int type;

        private Event() {
        }

        ByteBuf buf;

        private Event(ByteBuf buf) {
            this.buf = buf;
        }

        public static Event CloseEvent() {
            Event event = new Event();
            event.type = 0;
            return event;
        }

        public ByteBuf getBuf() {
            if (type == 0) {
                throw new IllegalArgumentException("close事件没有buf");
            }
            return this.buf;
        }

        public static Event WriteEvent(ByteBuf buf) {
            Event event = new Event(BufUtil.readCopy(buf));
            event.type = 1;
            return event;
        }

        public boolean isWrite() {
            return type == 1;
        }

        public boolean isClose() {
            return type == 0;
        }

        @Override
        public String toString() {
            if (isWrite()) {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.getBytes(0, bytes);
                return "写事件: 内容:" + new String(bytes, StandardCharsets.UTF_8);
            } else {
                return "关闭事件";
            }
        }
    }

    private static class ChannelEntry {

        private Channel channel;

        private int next;

        private final Map<Integer, String> recordMap = new ConcurrentHashMap<>();

        private final Map<Integer, Event> eventMap = new ConcurrentHashMap<>();

        {
            LogController.eventMaps.put(recordMap, eventMap);
        }

        public ChannelEntry(Channel channel) {
            this.channel = channel;
        }

        //保证同一channel上的事件按编号顺序执行
        public boolean run(int order, Event event) {
            recordMap.put(order, event.toString());
            if (order == next) {
                if (event.isWrite()) {
                    channel.writeAndFlush(event.getBuf());
                } else if (event.isClose()) {
                    System.out.printf("channel:%s关闭事件执行,消息序号为:%s\n", channel, order);
                    channel.close();
                    eventMap.forEach((od, e) -> {
                        if (e.isWrite()) {
                            if (od <= order) {
                                LogController.errors.add("不应该有关闭事件序号更小的响应事件");
                                System.out.println("不应该有比关闭事件序号更小的响应事件");
                            }
                            e.getBuf().release();
                        }
                    });
                }
                eventMap.remove(order);
                next++;
                if (eventMap.containsKey(next)) {
                    return run(next, eventMap.get(next));
                }
            } else if (order > next) {
                eventMap.put(order, event);
            } else {
                event.getBuf().release();
                LogController.errors.add("已经执行过的事件,order:" + order + ",next:" + next + ",event：" + event);
                return false;
            }
            return true;
        }


        public Channel getChannel() {
            return channel;
        }

        public void setChannel(Channel channel) {
            this.channel = channel;
        }

    }

}
