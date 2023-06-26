package com.ytyo.Utils;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.StandardCharsets;
import java.util.Optional;


public class AuthUtil {
    public static String preToken(String username, String password) {
        return HashUtils.sha1(username) + HashUtils.sha1(password);
    }

    public static Optional<String> finalToken(String preToken) {
        if (preToken == null || preToken.length() < 5) {
            return Optional.empty();
        }
        String finalToken = HashUtils.md5(preToken.substring(0, 5)) + HashUtils.sha256(preToken.substring(5));
        return Optional.of(finalToken);
    }

    //校验内容编码,生成preToken后编码为校验内容
    public static ByteBuf authEncode(String username, String password) {
        String preToken = AuthUtil.preToken(username, password);
        ByteBuf buffer = ByteBufAllocator.DEFAULT.heapBuffer();
        buffer.writeInt(preToken.length());
        buffer.writeBytes(preToken.getBytes(StandardCharsets.UTF_8));
        return buffer;
    }
}
