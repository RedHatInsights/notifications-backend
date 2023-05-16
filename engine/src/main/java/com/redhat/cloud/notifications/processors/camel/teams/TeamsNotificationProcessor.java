package com.redhat.cloud.notifications.processors.camel.teams;

import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor;

import javax.enterprise.context.ApplicationScoped;

/*
 * This processor transforms an incoming Teams notification, initially received as JSON data,
 * into a data structure that can be used by the Camel HTTP component to send a message to
 * the desired Teams channel.
 */
@ApplicationScoped
public class TeamsNotificationProcessor extends CamelNotificationProcessor {

    @Override
    protected String getIntegrationName() {
        return "Teams";
    }
}
