package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.processors.camel.CamelNotification;
import com.redhat.cloud.notifications.processors.camel.CamelProcessor;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.UUID;

@ApplicationScoped
public class TeamsProcessor extends CamelProcessor {

    @Inject
    @RestClient
    InternalTemporaryTeamsService internalTemporaryTeamsService;


    @Override
    protected String getIntegrationName() {
        return "Teams";
    }

    @Override
    protected String getIntegrationType() {
        return "teams";
    }

    @Override
    protected void sendNotification(Event event, Endpoint endpoint, UUID historyId) throws Exception {
        CamelNotification notification = getCamelNotification(event, endpoint, historyId);
        internalTemporaryTeamsService.send(notification);
    }
}
