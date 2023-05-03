package com.redhat.cloud.notifications.processors.google.chat;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.common.camel.CamelNotification;
import com.redhat.cloud.notifications.processors.common.camel.CamelProcessor;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class GoogleChatProcessor extends CamelProcessor {

    @Inject
    @RestClient
    InternalTemporaryGoogleChatService internalTemporaryGoogleSpacesService;

    protected String getIntegrationName() {
        return "Google Chat";
    }

    protected String getIntegrationType() {
        return "google_chat";
    }

    @Override
    protected void sendNotification(Event event, Endpoint endpoint, UUID historyId) throws Exception {
        CamelNotification notification = getCamelNotification(event, endpoint, historyId);
        internalTemporaryGoogleSpacesService.send(notification);
    }
}
