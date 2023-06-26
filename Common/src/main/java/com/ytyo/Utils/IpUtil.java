package com.ytyo.Utils;

public class IpUtil {
    public static String parse2Host(String ip){
        for (int i = 0; i < ip.length(); i++) {
            if (ip.charAt(i)==':') {
                return ip.substring(0,i);
            }
        }
        return ip;
    }
}
