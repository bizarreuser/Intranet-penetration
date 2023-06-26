package com.ytyo.Dispatcher.TertiumQuid.Pool;


import com.ytyo.Dispatcher.Receiver;
import com.ytyo.Dispatcher.TertiumQuid.ForwardChannel;
import com.ytyo.Utils.ConnectUtil;
import io.netty.channel.Channel;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ytyo.PenetrationClient.CONNECTION_COUNT;

//客户端端实现一个简单的连接池用于管理连接
public class ForwardChannelPool {
    private static final ArrayList<ForwardChannel> resourceList = new ArrayList<>();
    private static int next = -1;

    //为重连次数计数
    public static final AtomicInteger ConnectionCount = new AtomicInteger(0);

    public static Receiver receiver;

    public static synchronized void unAliveDetection() {
        new Thread(() -> {
            while (true) {
                try {
                    clearUnActive();
                    if (ConnectionCount.get() > 500) {
                        System.out.println("重连次数过多");
                        System.exit(-1);
                    }
                    if (resourceList.size() < CONNECTION_COUNT * 3 / 4) {
                        for (int i = 0; i < CONNECTION_COUNT - resourceList.size(); i++) {
                            TimeUnit.SECONDS.sleep(1);
                            ConnectionCount.incrementAndGet();
                            System.out.println("尝试重连");
                            receiver.connectServerForward();
                        }
                    }
                    if (resourceList.size() == 0) {
                        System.out.println("服务端未开启");
                        System.exit(0);
                    }
                    System.out.println("剩余" + resourceList.size() + "存活的连接");
                    TimeUnit.SECONDS.sleep(8);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }


    public static synchronized void clearUnActive() {
        resourceList.removeIf(forwardChannel -> {
            Channel channel = forwardChannel.channel();

            if (ConnectUtil.connectInActive(channel)) {
                channel.close();
                return true;
            } else
                return false;

        });
    }


    public static synchronized void add(ForwardChannel forwardChannel) {
        assert forwardChannel != null;
        resourceList.add(forwardChannel);
    }

    public static synchronized Optional<ForwardChannel> findResource() {
        if (resourceList.isEmpty()) {
            return Optional.empty();
        }

        boolean flag = false;
        for (ForwardChannel forwardChannel : resourceList) {
            if (ConnectUtil.connectActive(forwardChannel.channel())) {
                flag = true;
                break;
            }
        }
        if (!flag) {
            return Optional.empty();
        }

        next = (next + 1) % resourceList.size();
        ForwardChannel resource = resourceList.get(next);
        Channel channel = resource.channel();
        if (ConnectUtil.connectInActive(channel)) {
            return findResource();
        }
        return Optional.of(resource);
    }

    public static synchronized boolean remove(Channel channel) {
        return resourceList.removeIf(channelResource -> channelResource.channel() == channel);
    }


}