package com.redhat.cloud.notifications.utils;

public class LineBreakCleaner {

    public static String clean(String value) {
        if (value == null) {
            return null;
        } else {
            return value.replace("\r", "").replace("\n", "");
        }
    }
}
