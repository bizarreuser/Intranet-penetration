package com.ytyo.Id;

public class UserInfo {
    private static String UserName = "user";
    private static String Password = "123456";

    public static String username() {
        return UserName;
    }

    public static String password() {
        return Password;
    }

    public static void setUserName(String userName) {
        UserName = userName;
    }

    public static void setPassword(String password) {
        Password = password;
    }
}
