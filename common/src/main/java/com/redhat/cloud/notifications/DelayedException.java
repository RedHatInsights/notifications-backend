package com.redhat.cloud.notifications;

public class DelayedException extends RuntimeException {

    public DelayedException(String message) {
        super(message);
    }
}
