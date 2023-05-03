package com.redhat.cloud.notifications.processors.google.chat;

import com.redhat.cloud.notifications.processors.common.camel.CamelNotificationProcessor;
import javax.enterprise.context.ApplicationScoped;

/*
 * This processor transforms an incoming Google Chat notification, initially received as JSON data,
 * into a data structure that can be used by the Camel HTTP component to send a message to Google Chat.
 */
@ApplicationScoped
public class GoogleChatNotificationProcessor extends CamelNotificationProcessor {

    @Override
    protected String getIntegrationName() {
        return "Google Chat";
    }
}
