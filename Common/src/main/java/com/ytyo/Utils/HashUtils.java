package com.ytyo.Utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class HashUtils {

    private HashUtils() {
    }

    public static String md5(String input) {
        return hash(input, "MD5");
    }

    public static String sha1(String input) {
        return hash(input, "SHA-1");
    }

    public static String sha256(String input) {
        return hash(input, "SHA-256");
    }

    private static String hash(String input, String algorithm) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not supported: " + algorithm);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

