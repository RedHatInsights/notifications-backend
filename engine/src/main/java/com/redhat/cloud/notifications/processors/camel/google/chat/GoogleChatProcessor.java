package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import jakarta.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.events.EndpointProcessor.GOOGLE_CHAT_ENDPOINT_SUBTYPE;

@ApplicationScoped
public class GoogleChatProcessor extends CamelProcessor {

    protected String getIntegrationName() {
        return "Google Chat";
    }

    protected String getIntegrationType() {
        return GOOGLE_CHAT_ENDPOINT_SUBTYPE;
    }
}
