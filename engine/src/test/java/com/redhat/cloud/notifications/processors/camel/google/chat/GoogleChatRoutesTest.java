package com.redhat.cloud.notifications.processors.camel.google.chat;

import com.redhat.cloud.notifications.processors.camel.CamelRoutesTest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;

import static com.redhat.cloud.notifications.events.EndpointProcessor.GOOGLE_CHAT_ENDPOINT_SUBTYPE;
import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_GOOGLE_CHAT_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.camel.google.chat.GoogleChatRouteBuilder.GOOGLE_CHAT_ROUTE;

@QuarkusTest
@TestProfile(GoogleChatTestProfile.class)
public class GoogleChatRoutesTest extends CamelRoutesTest {

    @BeforeEach
    @Override
    protected void beforeEach() {
        routeEndpoint = "https://foo.com";
        mockRouteEndpoint = "mock:https:foo.com";
        super.beforeEach();
    }

    @Override
    protected String getIncomingRoute() {
        return GOOGLE_CHAT_ROUTE;
    }

    @Override
    protected String getEndpointSubtype() {
        return GOOGLE_CHAT_ENDPOINT_SUBTYPE;
    }

    @Override
    protected String getRetryCounterName() {
        return CAMEL_GOOGLE_CHAT_RETRY_COUNTER;
    }
}
