package com.ytyo;

import com.ytyo.Dispatcher.Receiver;
import com.ytyo.Dispatcher.TertiumQuid.Pool.ForwardChannelPool;
import io.netty.util.internal.StringUtil;
import org.apache.commons.cli.*;


public class PenetrationClient {

    public static final int CONNECTION_COUNT = 32;
    public static final int DEFAULT_SERVER_FORWARD_PORT = 7002;
    public static final int DEFAULT_REAL_SERVER_PORT = 8080;
    public static final String DEFAULT_SERVER_HOST = "localhost";

    public static void main(String[] args) {

        Options options = new Options();
        options.addOption("help", "help", false, "帮助信息");
        options.addOption("h", "host", true, "远程服务器ip");
        options.addOption("fp", "forwardPort", true, "远程服务器转发端口");
        options.addOption("rp", "realPort", true, "本地服务端口");
        // 创建命令行解析器
        CommandLineParser parser = new DefaultParser();
        try {
            // 解析命令行参数
            CommandLine cmd = parser.parse(options, args);

            if (cmd.hasOption("help")) {
                // 显示帮助信息并退出
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp("MainApp", options);
                return;
            }


            //默认远程转发端口
            int forwardPort = DEFAULT_SERVER_FORWARD_PORT;

            int realPort = DEFAULT_REAL_SERVER_PORT;

            String host = DEFAULT_SERVER_HOST;

            if (cmd.hasOption("fp")) {
                try {
                    String fp = cmd.getOptionValue("fp");
                    forwardPort = Integer.parseInt(fp);
                } catch (NumberFormatException ignored) {
                    System.out.println("请输入正确的远程服务器转发端口");
                    return;
                }
            }
            if (cmd.hasOption("host")) {
                host = cmd.getOptionValue("host");
                if (StringUtil.isNullOrEmpty(host)) {
                    System.out.println("请输入正确的远程服务器ip");
                    return;
                }
            }

            if (cmd.hasOption("rp")) {
                try {
                    String rp = cmd.getOptionValue("rp");
                    realPort = Integer.parseInt(rp);
                } catch (NumberFormatException ignored) {
                    System.out.println("请输入正确的本地服务端口");
                    return;
                }
            }

            Receiver receiver = new Receiver(realPort, forwardPort, host);
            ForwardChannelPool.receiver = receiver;
            new Thread(() -> {
                try {
                    for (int i = 0; i < CONNECTION_COUNT; i++) {
                        receiver.connectServerForward();
                    }
                    ForwardChannelPool.unAliveDetection();
                } catch (InterruptedException ignored) {
                }
            }).start();

        } catch (ParseException e) {
            System.err.println("解析命令行参数时出错：" + e.getMessage());
        }
    }

}
