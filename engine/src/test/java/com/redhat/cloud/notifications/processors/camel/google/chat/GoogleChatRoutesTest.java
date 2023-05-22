package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelRoutesTest;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;

import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_GOOGLE_CHAT_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatRouteBuilder.GOOGLE_CHAT_INCOMING_ROUTE;
import static com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatRouteBuilder.GOOGLE_CHAT_OUTGOING_ROUTE;

@QuarkusTest
public class GoogleChatRoutesTest extends CamelRoutesTest {


    @BeforeEach
    void beforeTest() {
        restPath = GoogleChatRouteBuilder.REST_PATH;
        mockPath = "/camel/google_chat";
        mockPathKo = "/camel/google_chat_ko";
        mockPathRetries = "/camel/google_chat_retries";
        mockRouteEndpoint = "mock:google_chat_test_routes";
        camelIncomingRouteName = GOOGLE_CHAT_INCOMING_ROUTE;
        camelOutgoingRouteName = GOOGLE_CHAT_OUTGOING_ROUTE;
        retryCounterName = CAMEL_GOOGLE_CHAT_RETRY_COUNTER;
    }
}
