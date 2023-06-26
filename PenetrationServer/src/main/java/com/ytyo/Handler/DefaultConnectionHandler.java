package com.ytyo.Handler;


import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import com.ytyo.Inteceptor.ForwardIpInterceptor;
import io.netty.channel.ChannelHandlerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DefaultConnectionHandler implements ConnectionHandler {

    @Autowired
    ForwardIpInterceptor forwardIpInterceptor;

    @Override
    public void preActive(ChannelHandlerContext ctx) {
        if (forwardIpInterceptor.intercept(ctx)) {
            ctx.close();
        }
    }

    @Override
    public void postOpenForward(ChannelOrder channelOrder, ForwardChannelPool forwardChannelPool) {
        forwardChannelPool.aliveDetection();
    }

}
