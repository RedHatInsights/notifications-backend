package com.redhat.cloud.notifications;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Base64Utils {

    /**
     * Encodes to a UTF-8 String the given {@code value} using the {@link java.util.Base64 Base64} encoding scheme.
     * @param value a UTF-8 String to encode
     * @return the UTF-8 encoded String or {@code null} if {@code value} is null
     */
    public static String encode(String value) {
        if (value == null) {
            return null;
        } else {
            return new String(Base64.getEncoder().encode(value.getBytes(UTF_8)), UTF_8);
        }
    }

    /**
     * Decodes to a UTF-8 String the given {@code value} using the {@link java.util.Base64 Base64} encoding scheme.
     * @param value a UTF-8 String to decode
     * @return the UTF-8 decoded String or {@code null} if {@code value} is null
     */
    public static String decode(String value) {
        if (value == null) {
            return null;
        } else {
            return new String(Base64.getDecoder().decode(value.getBytes(UTF_8)), UTF_8);
        }
    }
}
