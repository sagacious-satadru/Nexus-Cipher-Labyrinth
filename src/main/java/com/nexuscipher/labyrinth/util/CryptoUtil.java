package com.nexuscipher.labyrinth.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class CryptoUtil {
    public static byte[] calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}