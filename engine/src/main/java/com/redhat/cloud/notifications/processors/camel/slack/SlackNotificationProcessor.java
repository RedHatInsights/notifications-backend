package com.redhat.cloud.notifications.processors.camel.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.processors.camel.CamelNotification;
import com.redhat.cloud.notifications.processors.camel.CamelNotificationProcessor;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;

import javax.enterprise.context.ApplicationScoped;

/*
 * This processor transforms an incoming Slack notification, initially received as JSON data,
 * into a data structure that can be used by the Camel Slack component to send a message to
 * the desired Slack channel.
 */
@ApplicationScoped
public class SlackNotificationProcessor extends CamelNotificationProcessor {

    @Override
    protected void logProcessingMessage(final CamelNotification commonNotification, final String exchangeBody) throws JsonProcessingException {
        if (Log.isDebugEnabled()) {
            SlackNotification slackNotification = getSlackNotification(exchangeBody);
            Log.debugf("Processing Slack notification [orgId=%s, historyId=%s, webhookUrl=%s, channel=%s]",
                slackNotification.orgId, slackNotification.historyId, slackNotification.webhookUrl, slackNotification.channel);
        }
    }

    @Override
    protected void addExtraProperties(final Exchange exchange, final String exchangeBody) throws JsonProcessingException {
        SlackNotification slackNotification = getSlackNotification(exchangeBody);
        exchange.setProperty("channel", slackNotification.channel);
    }

    @Override
    public String getSource() {
        return "slack";
    }

    private SlackNotification getSlackNotification(final String exchangeBody) throws JsonProcessingException {
        return objectMapper.readValue(exchangeBody, SlackNotification.class);
    }
}
