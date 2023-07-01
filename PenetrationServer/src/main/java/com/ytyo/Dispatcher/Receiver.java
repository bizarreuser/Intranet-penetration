package com.ytyo.Dispatcher;

import com.ytyo.Controller.LogController;
import com.ytyo.Dispatcher.Message.Frame;
import com.ytyo.Dispatcher.Message.FrameCode;
import com.ytyo.Dispatcher.TertiumQuid.ForwardChannel;
import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import com.ytyo.Dispatcher.Message.Protocol;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * 接收外界连接和消息
 */
public class Receiver {
    private static final AttributeKey<Integer> ORDER_KEY = AttributeKey.valueOf("order");
    private final int port;


    public Receiver(int port) {
        this.port = port;
    }

    public void openReceiver(ChannelOrder channelOrder, ForwardChannelPool forwardChannelPool) throws InterruptedException {
        NioEventLoopGroup group = new NioEventLoopGroup();

        new ServerBootstrap()
                .channel(NioServerSocketChannel.class)
                //支持同时的更多并发连接
                .group(group)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) {
                        ChannelPipeline pipeline = nioSocketChannel.pipeline();

                        pipeline.addLast(
                                new ChannelInboundHandlerAdapter() {

                                    @Override
                                    public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                        System.out.println("外界连接...");
                                        try {
                                            Optional<ForwardChannel> channel = forwardChannelPool.findResource();
                                            if (channel.isEmpty()) {
                                                System.out.println("暂无客户端，拒绝外界连接");
                                                ctx.close();
                                                return;
                                            }

                                            //日志计数
                                            LogController.i.incrementAndGet();

                                            String uuid = UUID.randomUUID().toString();
                                            //加入编号表
                                            if (channelOrder.put(uuid, ctx.channel())) {
                                                channel.get().writeOpenFrame(uuid);
                                            } else {
                                                LogController.errors.add(String.format("ChannelOrder put时:uuid:%s or channel:%s already exists.", uuid, ctx.channel()));
                                                ctx.close();
                                                return;
                                            }

                                            //发送测试帧
                                            ByteBuf test = Protocol.testFrame();
                                            channel.get().writeAndFlush(test);
                                            test.release();
                                        } finally {
                                            super.channelActive(ctx);
                                        }

                                    }

                                    @Override
                                    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                        Optional<ForwardChannel> channel = forwardChannelPool.findResource();
                                        Optional<String> uuid = channelOrder.removeByChannel(ctx.channel());
                                        if (uuid.isPresent() && channel.isPresent()) {
                                            channel.get().writeForceCloseFrame(uuid.get());
                                        }

                                        super.channelInactive(ctx);
                                    }

                                    @Override
                                    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                        Optional<ForwardChannel> channel = forwardChannelPool.findResource();
                                        Optional<String> uuid = channelOrder.removeByChannel(ctx.channel());
                                        if (uuid.isPresent() && channel.isPresent()) {
                                            channel.get().writeForceCloseFrame(uuid.get());
                                        }

                                        super.exceptionCaught(ctx, cause);
                                    }

                                }
                        );

                        pipeline.addLast(
                                new ChannelInboundHandlerAdapter() {
                                    @Override
                                    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                        Channel channel = ctx.channel();
                                        ByteBuf buf = (ByteBuf) msg;
                                        Optional<String> uuid = channelOrder.getUUID(channel);
                                        Optional<ForwardChannel> resource = forwardChannelPool.findResource();
                                        if (resource.isPresent() && uuid.isPresent()) {
                                            int msgOrder = getChannelNextMsgOrder(channel);
                                            ForwardChannel forwardChannel = resource.get();
                                            System.out.println("服务方向client转发uuid为" + uuid.get() + ",消息序号为" + msgOrder + "的消息 ");
                                            ByteBuf byteBuf = Protocol.transferFrameEncode(uuid.get(), msgOrder, new Frame(FrameCode.MESSAGE, buf));
                                            forwardChannel.writeAndFlush(byteBuf);
                                            byteBuf.resetReaderIndex();
                                            System.out.println("这个被转发的消息为:" + byteBuf.toString(StandardCharsets.UTF_8));
                                            byteBuf.release();
                                        } else {
                                            LogController.errors.add("客户端还未连上,或表中还没有channel的编号,关闭与外界的连接:" + channel);
                                            ctx.close();
                                            System.out.println("客户端还未连上,或表中还没有channel的编号,关闭与外界的连接:" + channel);
                                        }

                                        super.channelRead(ctx, msg);
                                    }
                                });

                    }

                })
                .bind(port)
                .sync();
        System.out.println("接收端:"+port+",开启成功!");
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
}