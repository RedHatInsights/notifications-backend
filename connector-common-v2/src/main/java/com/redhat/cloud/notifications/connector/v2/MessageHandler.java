package com.redhat.cloud.notifications.connector.v2;

public interface MessageHandler {
    void handle(MessageContext context) throws Exception;
}
