package com.ytyo.initializer;

import com.ytyo.Constant.DefaultConst;
import com.ytyo.Dispatcher.ChannelWay;
import com.ytyo.Handler.ConnectionHandler;
import com.ytyo.Handler.DefaultConnectionHandler;
import com.ytyo.Utils.SpringUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ApplicationStarter implements ApplicationRunner, ApplicationContextAware {

    @Autowired
    DefaultConnectionHandler defaultConnectionHandler;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        SpringUtil.initContext(applicationContext);
    }

    @Override
    public void run(ApplicationArguments args) throws InterruptedException {
        List<String> forwardPorts = args.getOptionValues("forwardPort");
        List<String> receivePorts = args.getOptionValues("receivePort");

        int forwardPort = DefaultConst.DefaultForwardPort;
        int receivePort = DefaultConst.DefaultReceivePort;

        try {
            if (forwardPorts != null && !forwardPorts.isEmpty()) {
                forwardPort = Integer.parseInt(forwardPorts.get(0));
            }
        } catch (NumberFormatException ignored) {
        }
        try {
            if (receivePorts != null && !receivePorts.isEmpty()) {
                receivePort = Integer.parseInt(receivePorts.get(0));
            }
        } catch (NumberFormatException ignored) {
        }

        new ChannelWay(forwardPort, receivePort,defaultConnectionHandler).openWay();
    }


}
