package com.ytyo.Service;

import com.ytyo.Inteceptor.ForwardIpInterceptor;
import com.ytyo.Utils.AuthUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Service
public class UserService {


    static String uniqueToken = AuthUtil.finalToken(AuthUtil.preToken("user", "111111")).orElseThrow();

    /**
     * 校验并携带上为channel携带上校验信息
     */
    public boolean authenticate(Channel channel, ByteBuf buf) {
        if (buf == null || buf.readableBytes() < 5) {
            ForwardIpInterceptor.addCount(channel.remoteAddress().toString());
            return FalseAuth(channel);
        }

        Optional<String> preToken = getPreToken(buf);
        if (preToken.isEmpty()) {
            return FalseAuth(channel);
        }
        Optional<String> finalToken = AuthUtil.finalToken(preToken.get());
        if (finalToken.isEmpty()) {
            return FalseAuth(channel);
        }

        if (uniqueToken.equals(finalToken.get())) {
            return TrueAuth(channel);
        } else {
            return FalseAuth(channel);
        }

    }

    private boolean FalseAuth(Channel channel) {
        ForwardIpInterceptor.addCount(channel.remoteAddress().toString());
        return false;
    }

    private boolean TrueAuth(Channel channel) {
        ForwardIpInterceptor.passIp(channel.remoteAddress().toString());
        return true;
    }


    private Optional<String> getPreToken(ByteBuf buf) {
        if (buf == null || buf.readableBytes() <= 5) {
            return Optional.empty();
        }
        try {
            int len = buf.readInt();
            byte[] bytes = new byte[len];
            buf.readBytes(bytes);
            return Optional.of(new String(bytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

}
