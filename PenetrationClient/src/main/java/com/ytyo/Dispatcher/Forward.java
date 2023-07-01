package com.ytyo.Dispatcher;

import com.ytyo.Dispatcher.Message.Frame;
import com.ytyo.Dispatcher.Message.FrameCode;
import com.ytyo.Dispatcher.Message.Protocol;
import com.ytyo.Dispatcher.TertiumQuid.ForwardChannel;
import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public class Forward {
    private static final AttributeKey<Integer> ORDER_KEY = AttributeKey.valueOf("order");
    public final Bootstrap bootstrap;
    private final int realServerPort;

    //为相同的id实现同步锁
    private static final List<String> uuids = new ArrayList<>();

    //当内容相同时，返回同一个对象
    protected synchronized String internCache(String s) {
        int index = uuids.indexOf(s);
        if (index != -1) {
            return uuids.get(index);
        } else {
            uuids.add(s);
            return s;
        }
    }

    private synchronized void removeCache(String s) {
        uuids.remove(s);
    }


    public Forward(int realServerPort) {
        this.realServerPort = realServerPort;
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<NioSocketChannel>() {

                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) {
                        ChannelPipeline pipeline = nioSocketChannel.pipeline();

                        pipeline.addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                super.channelActive(ctx);
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                try {
                                    Optional<String> uuid = ChannelOrder.removeByChannel(ctx.channel());
                                    if (uuid.isPresent()) {
                                        System.out.println("与本地服务器的连接断开,channel:" + ctx.channel() + ",uuid:" + uuid);
                                        Optional<ForwardChannel> forwardChannel = ForwardChannelPool.findResource();
                                        if (forwardChannel.isEmpty()) {
                                            System.out.println("未连接到服务器,发送关闭帧失败");
                                            return;
                                        }
                                        int msgOrder = getChannelNextMsgOrder(ctx.channel());
                                        forwardChannel.get().writeCloseFrame(uuid.get(), msgOrder);
                                        removeCache(uuid.get());
                                        ctx.close();
                                    }
                                } finally {
                                    super.channelInactive(ctx);
                                }
                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                try {
                                    Optional<String> uuid = ChannelOrder.removeByChannel(ctx.channel());
                                    if (uuid.isPresent()) {
                                        System.out.println("与本地服务器的连接断开,channel:" + ctx.channel() + ",uuid:" + uuid);
                                        Optional<ForwardChannel> forwardChannel = ForwardChannelPool.findResource();
                                        if (forwardChannel.isEmpty()) {
                                            System.out.println("未连接到服务器,发送关闭帧失败");
                                            return;
                                        }
                                        int msgOrder = getChannelNextMsgOrder(ctx.channel());
                                        forwardChannel.get().writeCloseFrame(uuid.get(), msgOrder);
                                        removeCache(uuid.get());
                                        ctx.close();
                                    }
                                } finally {
                                    super.exceptionCaught(ctx, cause);
                                }
                            }

                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

                                ByteBuf respBuf = (ByteBuf) msg;
                                Channel channel = ctx.channel();
                                System.out.println(channel + "得到响应");
                                System.out.println("响应内容: " + respBuf.toString(StandardCharsets.UTF_8));
                                respBuf.resetReaderIndex();
                                Optional<String> uuid = ChannelOrder.getUUID(channel);
                                Optional<ForwardChannel> resource = ForwardChannelPool.findResource();
                                if (resource.isPresent() && uuid.isPresent()) {
                                    int msgOrder = getChannelNextMsgOrder(channel);
                                    ForwardChannel forwardChannel = resource.get();
                                    ByteBuf byteBuf = Protocol.transferFrameEncode(uuid.get(), msgOrder, new Frame(FrameCode.MESSAGE, respBuf));
                                    forwardChannel.writeAndFlush(byteBuf);
                                    System.out.println("客户端上传了uuid为:" + uuid + ",消息序号为:" + msgOrder + " 的消息");
                                    byteBuf.release();
                                } else {
                                    System.out.println("响应返回给Server失败");
                                }
                                super.channelRead(ctx, msg);
                            }
                        });
                    }
                });

    }


    private int getChannelNextMsgOrder(Channel channel) {
        synchronized (channel) {
            Attribute<Integer> orderAttribute = channel.attr(ORDER_KEY);

            int order;
            if (orderAttribute.get() == null) {
                order = 0;
            } else {
                order = orderAttribute.get() + 1;
            }
            orderAttribute.set(order);

            return order;
        }
    }

    public Optional<Channel> connectRealServer() {
        try {
            return Optional.of(bootstrap
                    .connect(new InetSocketAddress("localhost", realServerPort))
                    .sync()
                    .channel());
        } catch (Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }
    }


}
