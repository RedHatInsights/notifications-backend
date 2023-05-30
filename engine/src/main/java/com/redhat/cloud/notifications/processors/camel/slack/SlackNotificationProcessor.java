package com.redhat.cloud.notifications.processors.camel.slack;

import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor;
import org.apache.camel.Exchange;

import javax.enterprise.context.ApplicationScoped;

/*
 * This processor transforms an incoming Slack notification, initially received as JSON data,
 * into a data structure that can be used by the Camel Slack component to send a message to
 * the desired Slack channel.
 */
@ApplicationScoped
public class SlackNotificationProcessor extends CamelNotificationProcessor<SlackNotification> {

    @Override
    protected Class<SlackNotification> getNotificationClass() {
        return SlackNotification.class;
    }

    @Override
    public String getConnectorName() {
        return "slack";
    }

    @Override
    protected void addExtraProperties(final Exchange exchange, SlackNotification notification) {
        exchange.setProperty("channel", notification.channel);
    }
}
