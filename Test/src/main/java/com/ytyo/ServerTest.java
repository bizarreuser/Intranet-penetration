package com.ytyo;

import com.ytyo.Dispatcher.Message.Protocol;
import com.ytyo.Utils.ConnectUtil;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerTest {
    public static void main(String[] args) throws InterruptedException {
        NioEventLoopGroup eventExecutors = new NioEventLoopGroup();
        CopyOnWriteArrayList<Channel> list = new CopyOnWriteArrayList<>();
        new ServerBootstrap()
                .group(eventExecutors)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<NioSocketChannel>() {
                                  @Override
                                  protected void initChannel(NioSocketChannel nioSocketChannel) {
                                      ChannelPipeline pipeline = nioSocketChannel.pipeline();
                                      pipeline.addLast(new LengthFieldBasedFrameDecoder(Integer.MAX_VALUE, 0, 4, 0, 4));

                                      pipeline.addLast(new ChannelInboundHandlerAdapter() {
                                          @Override
                                          public void channelActive(ChannelHandlerContext ctx) throws Exception {
                                              System.out.println("新连接...");
                                              list.add(ctx.channel());
                                              super.channelActive(ctx);
                                          }

                                          @Override
                                          public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                                              System.out.println(((ByteBuf) msg).toString(StandardCharsets.UTF_8));
                                              super.channelRead(ctx, msg);
                                          }

                                          @Override
                                          public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                                              System.out.println("一个连接断开");
                                              super.channelInactive(ctx);
                                          }
                                      });

                                      pipeline.addLast(new IdleStateHandler(45, 10, 0));
                                      pipeline.addLast(new ChannelDuplexHandler() {
                                                           @Override
                                                           public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                                               if (evt instanceof IdleStateEvent e) {
                                                                   if (e.state() == IdleState.READER_IDLE) {
                                                                       System.out.println("读空闲");
                                                                      //ctx.close();
                                                                   } else if (e.state() == IdleState.WRITER_IDLE) {
                                                                       System.out.println("写空闲");
                                                                       ByteBuf buffer = ByteBufAllocator.DEFAULT.heapBuffer();
                                                                       buffer.writeBytes(LocalDateTime.now().toString().repeat(5).getBytes(StandardCharsets.UTF_8));
                                                                       ctx.writeAndFlush(Protocol.transferEncode(buffer));
                                                                       buffer.release();
                                                                   }
                                                               }
                                                           }
                                                       }
                                      );
                                  }


                              }
                ).bind(8000)
                .sync();

        Scanner scanner = new Scanner(System.in);
        while (true) {
            String next = scanner.next();
            for (Channel channel : list) {
                if (ConnectUtil.connectActive(channel)) {
                    ByteBuf buf = ByteBufAllocator.DEFAULT.heapBuffer();
                    buf.writeBytes(next.getBytes(StandardCharsets.UTF_8));
                    channel.writeAndFlush(Protocol.transferEncode(buf));
                    buf.release();
                }else {
                    System.out.println("连接失活");
                }
            }
        }
    }
}
