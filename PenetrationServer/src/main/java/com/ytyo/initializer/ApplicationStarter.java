package com.ytyo.initializer;

import com.ytyo.Constant.DefaultConst;
import com.ytyo.Dispatcher.ChannelWay;
import com.ytyo.Handler.ConnectionHandler;
import com.ytyo.Handler.DefaultConnectionHandler;
import com.ytyo.Id.IdProvider;
import com.ytyo.Utils.SpringUtil;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

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

        String[] sourceArgs = args.getSourceArgs();

        List<String> forwardPorts = args.getOptionValues("forwardPort");
        List<String> receivePorts = args.getOptionValues("receivePort");
        List<String> users = args.getOptionValues("user");
        List<String> passwords = args.getOptionValues("password");


        int forwardPort = DefaultConst.DefaultForwardPort;
        int receivePort = DefaultConst.DefaultReceivePort;

        String user = null, password = null;


        if (users != null && !users.isEmpty()) {
            user = users.get(0);
        }
        if (passwords != null && !passwords.isEmpty()) {
            password = passwords.get(0);
        }

        if (StringUtils.hasText(user) != StringUtils.hasText(password)) {
            System.out.println("请输入预设的账号和密码");
            System.exit(0);
        }
        if (StringUtils.hasText(user)) {
            if (!IdProvider.setUserPassWd(user, password)) {
                System.out.println("账号或密码过于简单");
                System.exit(0);
            }
        }


        try {
            if (receivePorts != null && !receivePorts.isEmpty()) {
                receivePort = Integer.parseInt(receivePorts.get(0));
            }
        } catch (NumberFormatException ignored) {
        }

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

        new ChannelWay(forwardPort, receivePort, defaultConnectionHandler).openWay();
    }


}
