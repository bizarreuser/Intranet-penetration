package com.ytyo.Dispatcher.TertiumQuid.Pool;


import com.ytyo.Dispatcher.TertiumQuid.ForwardChannel;
import com.ytyo.Utils.ConnectUtil;
import io.netty.channel.Channel;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

//服务端实现一个简单的连接池用于管理连接
public class ForwardChannelPool {
    public final List<ForwardChannel> resourceList = new CopyOnWriteArrayList<>();

    private int next = -1;

    //存活连接数探测
    public void aliveDetection() {
        new Thread(() -> {
            while (true) {
                try {
                    TimeUnit.SECONDS.sleep(5);
                    clearUnActive();
                    System.out.println("剩余" + resourceList.size() + "存活的连接");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }).start();
    }

    public synchronized void clearUnActive() {
        resourceList.removeIf(forwardChannel -> {
            Channel channel = forwardChannel.channel();

            //清理不活跃的连接
            if (ConnectUtil.connectInActive(channel)) {
                channel.close();
                return true;
            } else {
                return false;
            }

        });
    }


    public synchronized void register(@NonNull ForwardChannel forwardChannel) {
        resourceList.add(forwardChannel);
    }

    public synchronized Optional<ForwardChannel> findResource() {
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

    public synchronized boolean remove(Channel channel) {
        return resourceList.removeIf(channelResource -> channelResource.channel() == channel);
    }


    public synchronized boolean isEmpty() {
        return resourceList.isEmpty();
    }
}