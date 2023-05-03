package com.redhat.cloud.notifications.processors.common.camel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.processors.slack.SlackNotification;
import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

/*
 * This processor transforms an incoming Slack notification, initially received as JSON data,
 * into a data structure that can be used by the Camel Slack component to send a message to
 * the desired Slack channel.
 */
@ApplicationScoped
public class CamelNotificationProcessor implements Processor {

    @Inject
    ObjectMapper objectMapper;

    @Override
    public void process(Exchange exchange) throws Exception {

        // First, we need to parse the incoming JSON data into a SlackNotification.
        Message in = exchange.getIn();
        String body = in.getBody(String.class);
        SlackNotification slackNotification = objectMapper.readValue(body, SlackNotification.class);

        // Then, we're using fields from the parsed SlackNotification to build the outgoing data.
        exchange.setProperty("orgId", slackNotification.orgId);
        exchange.setProperty("historyId", slackNotification.historyId);
        exchange.setProperty("webhookUrl", slackNotification.webhookUrl);
        exchange.setProperty("channel", slackNotification.channel);
        in.setBody(slackNotification.message);

        Log.debugf("Processing Slack notification [orgId=%s, historyId=%s, webhookUrl=%s, channel=%s]",
                slackNotification.orgId, slackNotification.historyId, slackNotification.webhookUrl, slackNotification.channel);
    }
}
