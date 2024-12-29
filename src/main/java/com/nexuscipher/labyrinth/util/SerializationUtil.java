package com.nexuscipher.labyrinth.util;

import java.io.*;

public class SerializationUtil {
    public static byte[] serialize(Object obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        }
    }

    public static Object deserialize(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = new ObjectInputStream(bis)) {
            return ois.readObject();
        }
    }
}