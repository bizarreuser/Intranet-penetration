package com.ytyo.Id;

import com.ytyo.Utils.AuthUtil;

import java.util.Optional;

public class IdProvider {
    private static String idToken = AuthUtil.finalToken(AuthUtil.preToken("user", "123456")).orElseThrow();

    public static String idToken() {
        return idToken;
    }

    public static boolean setUserPassWd(String user, String passWord) {
        Optional<String> token = AuthUtil.finalToken(AuthUtil.preToken(user, passWord));
        if (token.isPresent()) {
            idToken = token.get();
            return true;
        } else {
            return false;
        }
    }
}
