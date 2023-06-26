package com.ytyo.Handler;

import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import io.netty.channel.ChannelHandlerContext;

public interface ConnectionHandler {

    default void preInactive(ChannelHandlerContext ctx) {
    }

    default void postInactive(ChannelHandlerContext ctx) {
    }

    default void postException(ChannelHandlerContext ctx, Throwable cause) {
    }

    default void preException(ChannelHandlerContext ctx, Throwable cause) {
    }

    default void postRead(ChannelHandlerContext ctx, Object msg) {
    }

    default void preRead(ChannelHandlerContext ctx, Object msg) {
    }

    default void postActive(ChannelHandlerContext ctx) {
    }

    default void preActive(ChannelHandlerContext ctx) {
    }

    default void preOpenForward(ChannelOrder channelOrder, ForwardChannelPool forwardChannelPool) {
    }

    default void postOpenForward(ChannelOrder channelOrder, ForwardChannelPool forwardChannelPool) {
    }
}
