package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelProcessor;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.events.EndpointProcessor.TEAMS_ENDPOINT_SUBTYPE;

@ApplicationScoped
public class TeamsProcessor extends CamelProcessor {

    @Override
    protected String getIntegrationName() {
        return "Teams";
    }

    @Override
    protected String getIntegrationType() {
        return TEAMS_ENDPOINT_SUBTYPE;
    }
}
