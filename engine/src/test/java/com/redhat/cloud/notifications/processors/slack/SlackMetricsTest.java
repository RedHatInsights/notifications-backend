package com.redhat.cloud.notifications.processors.slack;

import com.redhat.cloud.notifications.processors.camel.CamelMetricsTest;
import com.redhat.cloud.notifications.processors.google.chat.GoogleChatRouteBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_SLACK_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.SLACK_OUTGOING_ROUTE;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.SLACK_INCOMING_ROUTE;

@QuarkusTest
public class MetricsTest extends CamelMetricsTest {

    @BeforeEach
    void beforeTest() {
        restPath = SlackRouteBuilder.REST_PATH;
        mockPath = "/camel/slack";
        mockPathKo = "/camel/slack_ko";
        camelIncomingRouteName = SLACK_INCOMING_ROUTE;
        camelOutgoingRouteName = SLACK_OUTGOING_ROUTE;
        retryCounterName = CAMEL_SLACK_RETRY_COUNTER;
    }

    @Override
    protected Object buildNotification(String webhookUrl) {
        return SlackRouteBuilderTest.buildNotification(webhookUrl);
    }
}
