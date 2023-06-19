package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor;

import javax.enterprise.context.ApplicationScoped;

/*
 * This processor transforms an incoming Google Chat notification, initially received as JSON data,
 * into a data structure that can be used by the Camel HTTP component to send a message to
 * the desired Google Chat space.
 */
@ApplicationScoped
public class GoogleChatNotificationProcessor extends CamelNotificationProcessor {

    @Override
    public String getConnectorName() {
        return "google_chat";
    }
}
