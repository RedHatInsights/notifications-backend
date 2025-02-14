package com.redhat.cloud.notifications.utils;

public class ActionParsingException extends RuntimeException {

    public ActionParsingException(String message) {
        super(message);
    }

    public ActionParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
