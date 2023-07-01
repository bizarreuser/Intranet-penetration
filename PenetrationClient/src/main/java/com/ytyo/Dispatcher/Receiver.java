package com.ytyo.Dispatcher;

import com.ytyo.Constant.Const;
import com.ytyo.Dispatcher.Message.Frame;
import com.ytyo.Dispatcher.Message.Protocol;
import com.ytyo.Dispatcher.TertiumQuid.ForwardChannel;
import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import com.ytyo.Id.UserInfo;
import com.ytyo.Utils.AuthUtil;
import com.ytyo.Utils.ConnectUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;


public class Receiver {

    private final Bootstrap bootstrap;
    private final Forward forward;
    private final int serverForwardPort;

    private final String host;
    private final AtomicInteger test = new AtomicInteger();

    private static final AttributeKey<String> AUTH = AttributeKey.valueOf(Const.Authenticated);

    public Receiver(int realServerPort, int serverForwardPort, String host) {
        forward = new Forward(realServerPort);
        this.serverForwardPort = serverForwardPort;
        this.host = host;
        NioEventLoopGroup group = new NioEventLoopGroup();
        bootstrap = new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                //设置写缓存高低水位线
                .option(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(1024 * 1024, 8 * 1024 * 1024))
                .handler(new ChannelInitializer<NioSocketChannel>() {

                    @Override
                    protected void initChannel(NioSocketChannel nioSocketChannel) {
                        ChannelPipeline pipeline = nioSocketChannel.pipeline();
                        pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));
                        pipeline.addLast(new ChannelInboundHandlerAdapter() {

                            @Override
                            public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                //让服务端校验身份
                                ByteBuf auth = AuthUtil.authEncode(UserInfo.username(), UserInfo.password());
                                ctx.channel().writeAndFlush(Protocol.transferEncode(auth));
                                auth.release();
                                Channel channel = ctx.channel();
                                ForwardChannelPool.add(new ForwardChannel(channel));
                                super.channelActive(ctx);
                            }

                            @Override
                            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                Channel channel = ctx.channel();
                                ForwardChannelPool.remove(channel);
                                channel.close();
                                System.out.println("与服务端的连接断开" + channel);
                                super.channelInactive(ctx);
                            }


                            @Override
                            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

                                try {
                                    ByteBuf respBuf = (ByteBuf) msg;
                                    Protocol.Entry entry = Protocol.decode(respBuf).orElseThrow();//这里异常就是协议错误
                                    String uuid = entry.getUUID();
                                    int msgOrder = entry.getMsgOrder();
                                    Frame frame = entry.getFrame();

                                    ByteBuf buf = frame.content();
                                    switch (frame.code()) {
                                        case MESSAGE -> {
                                            System.out.printf("客户端 收到消息: uuid:%s,order:%s,消息:%s", uuid, msgOrder, buf.toString(StandardCharsets.UTF_8));
                                            respBuf.resetReaderIndex();
                                            Optional<ChannelOrder.ChannelEntry> optionalOldEntry;
                                            Channel getChannel = null;
                                            synchronized (forward.internCache(uuid)) {
                                                optionalOldEntry = ChannelOrder.getChannelEntry(uuid);
                                                if (optionalOldEntry.isEmpty()) {
                                                    Optional<Channel> newChannel = forward.connectRealServer();
                                                    if (newChannel.isEmpty()) {
                                                        System.out.println("新建到本地服务器的连接失败");
                                                    } else {
                                                        System.out.println("请求消息时建立本地连接,uuid:" + uuid);
                                                        ChannelOrder.put(uuid, newChannel.get());
                                                        getChannel = newChannel.get();
                                                    }
                                                } else {
                                                    getChannel = optionalOldEntry.get().getChannel();
                                                }
                                            }
                                            if (getChannel != null && ConnectUtil.connectActive(getChannel)) {
                                                System.out.printf("uuid:%s,向本地服务器发送:%s\n", uuid, buf.toString(StandardCharsets.UTF_8));
                                                buf.resetReaderIndex();
                                                if (!ChannelOrder.reqByOrder(uuid, msgOrder, buf)) {
                                                    System.out.printf("请求消息失败,uuid:%s,order:%s,消息:%s\n", uuid, msgOrder, buf.toString(StandardCharsets.UTF_8));
                                                }
                                            } else {
                                                ctx.writeAndFlush(Protocol.forceCloseFrame(uuid));
                                            }

                                        }
                                        case OPEN -> {
                                            System.out.println("Open The Local Channel");
                                            synchronized (forward.internCache(uuid)) {
                                                Optional<Channel> channel = ChannelOrder.getChannel(uuid);
                                                if (channel.isEmpty()) {
                                                    Optional<Channel> newChannel = forward.connectRealServer();
                                                    if (newChannel.isPresent()) {
                                                        System.out.println("open帧: 本地连接建立!channel=" + newChannel.get());
                                                        ChannelOrder.put(uuid, newChannel.get());
                                                        ctx.writeAndFlush(Protocol.openAckFrame(uuid));
                                                    } else {
                                                        ctx.writeAndFlush(Protocol.forceCloseFrame(uuid));
                                                        System.out.println("与本地服务器连接失败");
                                                    }
                                                }
                                            }
                                        }
                                        case CLOSE -> {
                                            System.out.println("Close The Local Channel");
                                            Optional<Channel> channel = ChannelOrder.getChannel(uuid);
                                            if (channel.isPresent() && ConnectUtil.connectActive(channel.get())) {
                                                channel.get().close();
                                            }
                                            ctx.writeAndFlush(Protocol.closeAckFrame(uuid));//关闭确认,目前无作用
                                        }
                                        case AUTH -> {
                                            if ("true".equals(uuid)) {
                                                System.out.println("校验成功");
                                                ctx.channel().attr(AUTH);
                                            } else {
                                                System.out.println("账号或密码错误");
                                                ctx.close();
                                                System.exit(0);
                                            }
                                        }

                                        case TEST -> {
                                            System.out.println(test.incrementAndGet());
                                            Optional<ForwardChannel> resource = ForwardChannelPool.findResource();
                                            if (resource.isPresent()) {
                                                ByteBuf replyFrame = Protocol.testReplyFrame();
                                                resource.get().writeAndFlush(replyFrame);
                                                replyFrame.release();
                                            } else {
                                                System.out.println("与服务端的连接不活跃，导致丢消息");
                                            }

                                        }
                                        default -> {

                                        }
                                    }
                                    buf.release();
                                } finally {
                                    super.channelRead(ctx, msg);
                                }
                            }
                        });

                        pipeline.addLast(new IdleStateHandler(45, 15, 0));
                        pipeline.addLast(new ChannelDuplexHandler() {
                            @Override
                            public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {

                                //判断Authenticated, 校验后再ping
                                if (ctx.channel().hasAttr(AUTH)) {
                                    if (evt instanceof IdleStateEvent e) {
                                        if (e.state() == IdleState.READER_IDLE) {
                                            ctx.close();
                                        } else if (e.state() == IdleState.WRITER_IDLE) {
                                            ctx.writeAndFlush(Protocol.ping());
                                        }
                                    }
                                }

                                super.userEventTriggered(ctx, evt);
                            }
                        });
                    }
                });

    }

    public void connectServerForward() throws InterruptedException {
        try {
            bootstrap
                    .connect(new InetSocketAddress(host, serverForwardPort))
                    .sync();
        } catch (InterruptedException | RuntimeException e) {
            e.printStackTrace();
        }
    }


}
