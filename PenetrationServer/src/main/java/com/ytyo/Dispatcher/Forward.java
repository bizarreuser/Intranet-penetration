package com.ytyo.Dispatcher;

import com.ytyo.Constant.DefaultConst;
import com.ytyo.Controller.LogController;
import com.ytyo.Dispatcher.Message.Frame;
import com.ytyo.Dispatcher.TertiumQuid.ForwardChannel;
import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import com.ytyo.Handler.ConnectionHandler;
import com.ytyo.Service.UserService;
import com.ytyo.Dispatcher.Message.Protocol;
import com.ytyo.Utils.ConnectUtil;
import com.ytyo.Utils.SpringUtil;
import com.ytyo.Utils.IpUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Forward {

    private static final AttributeKey<String> AUTH_ATTR = AttributeKey.valueOf(DefaultConst.Authenticated);
    private final int port;

    private final ConnectionHandler handler;

    private final UserService userService;

    public volatile String CurrentIp;

    //日志,简单的用于测试丢包率的计算
    public static final AtomicInteger testReply = new AtomicInteger();

    public Forward(int port, ConnectionHandler handler) {
        userService = SpringUtil.getBean(UserService.class);
        this.port = port;
        this.handler = handler;
    }


    public void openForward(ChannelOrder channelOrder, ForwardChannelPool forwardChannelPool) throws InterruptedException {
        handler.preOpenForward(channelOrder, forwardChannelPool);
        NioEventLoopGroup group = new NioEventLoopGroup();
        new ServerBootstrap()
                .group(group)
                //设置连接写缓存的高低水位线，使得isWriteable不容易返回false
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 8 * 1024 * 1024))
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {

                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) {
                        ChannelPipeline pipeline = nioSocketChannel.pipeline();

                        pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                        pipeline.addLast(new ChannelInboundHandlerAdapter() {


                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                handler.preActive(ctx);
                                SocketAddress remoteAddress = ctx.channel().remoteAddress();
                                if (remoteAddress instanceof InetSocketAddress address) {
                                    String remoteIp = address.getHostString();
                                    synchronized (group) {
                                        if (CurrentIp == null) {
                                            CurrentIp = remoteIp;
                                            System.out.println(remoteIp + "的Client连接....");
                                        } else if (CurrentIp.equals(remoteIp)) {
                                            System.out.println(remoteIp + "的Client连接....");
                                        } else {
                                            System.out.println("不可同时有其他主机连接上来");
                                            System.out.println("ip:" + remoteIp + ", 当前ip: " + CurrentIp);
                                            ctx.channel().close();
                                        }
                                    }
                                } else {
                                    ctx.close();
                                }
                                handler.postActive(ctx);
                                super.channelActive(ctx);
                            }


                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                Channel readChannel = ctx.channel();
                                ByteBuf respBuf = (ByteBuf) msg;
                                try {
                                    handler.preRead(ctx, msg);
                                    //在第一次read时进行校验
                                    if (!readChannel.hasAttr(AUTH_ATTR)) {
                                        if (!userService.authenticate(readChannel, (ByteBuf) msg)) {
                                            readChannel.writeAndFlush(Protocol.auth(false));
                                            TimeUnit.SECONDS.sleep(2);//等两秒待客户端收到
                                            ctx.close();
                                            return;
                                        }

                                        readChannel.writeAndFlush(Protocol.auth(true));
                                        readChannel.attr(AUTH_ATTR);
                                        forwardChannelPool.register(new ForwardChannel(readChannel));
                                        return;
                                    }

                                    //处理该响应消息
                                    Protocol.Entry entry = Protocol.decode(respBuf).orElseThrow(() -> {
                                        LogController.errors.add("错误的协议内容");
                                        return new RuntimeException("错误的协议内容");
                                    });

                                    String uuid = entry.getUUID();
                                    int msgOrder = entry.getMsgOrder();
                                    Frame frame = entry.getFrame();

                                    ByteBuf buf = frame.content();
                                    switch (frame.code()) {
                                        case MESSAGE -> {
                                            if (channelOrder.respByOrder(uuid, msgOrder, buf)) {
                                                buf.resetReaderIndex();
                                                System.out.println("响应到外界的消息为:" + buf.toString(StandardCharsets.UTF_8));
                                            } else {
                                                buf.resetReaderIndex();
                                                LogController.errors.add("响应到外界失败，消息前面部分:" + buf.toString(StandardCharsets.UTF_8).substring(0, 15));
                                                buf.resetReaderIndex();
                                                System.out.println("响应到外界失败，消息:" + buf.toString(StandardCharsets.UTF_8));
                                            }
                                        }
                                        case CLOSE -> {
                                            boolean suc = true;
                                            Optional<Channel> channel = channelOrder.getChannel(uuid);
                                            if (channel.isPresent() && ConnectUtil.connectActive(channel.get())) {
                                                if (msgOrder > -1) {
                                                    System.out.println("uuid:" + uuid + ",消息序号:" + msgOrder + " 执行closeByOrder");
                                                    if (!channelOrder.closeByOrder(uuid, msgOrder)) {
                                                        suc = false;
                                                        LogController.errors.add(String.format("通过关闭帧关闭uuid为%s的channel失败,关闭帧序号为%s%n", uuid, msgOrder));
                                                        System.out.printf("通过关闭帧关闭uuid为%s的channel失败,关闭帧序号为%s%n", uuid, msgOrder);
                                                    }
                                                } else {
                                                    channel.get().close();
                                                }
                                            }

                                            if (suc)
                                                ctx.writeAndFlush(Protocol.closeAckFrame(uuid));//目前无作用
                                        }
                                        case OPEN_ACK -> {
                                            //todo

                                        }
                                        case CLOSE_ACK -> {
                                            //todo
                                        }
                                        case TEST_REPLY -> {

                                        }
                                    }
                                    buf.release();
                                    handler.postRead(ctx, msg);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                } finally {
                                    super.channelRead(ctx, msg);
                                }

                            }

                            @Override
                            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
                                handler.preException(ctx, cause);
                                Channel channel = ctx.channel();
                                synchronized (group) {
                                    if (forwardChannelPool.remove(channel)) {
                                        channel.close();
                                        System.out.println(channel + "服务端与客户端连接断开...");
                                        if (forwardChannelPool.isEmpty()) {
                                            CurrentIp = null;
                                            System.out.println("一个客户端断开了所有连接");
                                        }
                                    }
                                }
                                super.exceptionCaught(ctx, cause);//异常在这里内部就处理了，并打印

                            }


                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                handler.preInactive(ctx);
                                Channel channel = ctx.channel();
                                synchronized (group) {
                                    if (forwardChannelPool.remove(channel)) {
                                        channel.close();
                                        System.out.println(channel + "服务端与客户端连接断开...");
                                        if (forwardChannelPool.isEmpty()) {
                                            CurrentIp = null;
                                            System.out.println("一个客户端断开了所有连接");
                                        }
                                    }
                                }
                                handler.postInactive(ctx);
                                super.channelInactive(ctx);
                            }


                        });

                        pipeline.addLast(new IdleStateHandler(45, 15, 0));
                        pipeline.addLast(new ChannelDuplexHandler() {
                                             @Override
                                             public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                                 if (evt instanceof IdleStateEvent e) {
                                                     if (e.state() == IdleState.READER_IDLE) {
                                                         ctx.close();
                                                     } else if (e.state() == IdleState.WRITER_IDLE) {
                                                         ctx.writeAndFlush(Protocol.ping());
                                                     }
                                                 }
                                             }
                                         }
                        );
                    }
                })
                .bind(port)
                .sync();
        handler.postOpenForward(channelOrder, forwardChannelPool);
        System.out.println("转发端:" + port + ",开启成功!");
    }


}
