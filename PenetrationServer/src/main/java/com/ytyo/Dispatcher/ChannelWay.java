package com.ytyo.Dispatcher;


import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import com.ytyo.Dispatcher.TertiumQuid.Table.ChannelOrder;
import com.ytyo.Handler.ConnectionHandler;

//创建并执行openWay，即开通通道
public class ChannelWay {
    Forward forward;
    Receiver receiver;

    ChannelOrder channelOrder;

    ForwardChannelPool forwardChannelPool;



    public ChannelWay(int forwardPort, int receiverPort, ConnectionHandler connectionHandler) {
        this.forward = new Forward(forwardPort, connectionHandler);
        this.receiver = new Receiver(receiverPort);
        channelOrder = new ChannelOrder();
        forwardChannelPool = new ForwardChannelPool();
    }

    public void openWay() throws InterruptedException {
        forward.openForward(channelOrder, forwardChannelPool);
        receiver.openReceiver(channelOrder, forwardChannelPool);
    }
}
