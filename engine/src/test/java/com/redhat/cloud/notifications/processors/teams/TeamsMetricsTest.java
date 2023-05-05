package com.redhat.cloud.notifications.processors.teams;

import com.redhat.cloud.notifications.processors.camel.CamelMetricsTest;
import com.redhat.cloud.notifications.processors.google.chat.GoogleChatRouteBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_GOOGLE_CHAT_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.google.chat.GoogleChatRouteBuilder.GOOGLE_CHAT_INCOMING_ROUTE;
import static com.redhat.cloud.notifications.processors.google.chat.GoogleChatRouteBuilder.OUTGOING_ROUTE;

@QuarkusTest
public class TeamsMetricsTest extends CamelMetricsTest {

    @BeforeEach
    void beforeTest() {
        restPath = GoogleChatRouteBuilder.REST_PATH;
        mockPath = "/camel/googlechat";
        mockPathKo = "/camel/googlechat_ko";
        camelIncomingRouteName = GOOGLE_CHAT_INCOMING_ROUTE;
        camelOutgoingRouteName = OUTGOING_ROUTE;
        retryCounterName = CAMEL_GOOGLE_CHAT_RETRY_COUNTER;
    }
}
