package com.redhat.cloud.notifications.processors.google.chat;

import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.MicrometerAssertionHelper;
import com.redhat.cloud.notifications.processors.camel.CamelMetricsTest;
import com.redhat.cloud.notifications.processors.slack.SlackNotification;
import com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.HttpResponse;
import javax.inject.Inject;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getClient;
import static com.redhat.cloud.notifications.MockServerLifecycleManager.getMockServerUrl;
import static com.redhat.cloud.notifications.models.HttpType.POST;
import static com.redhat.cloud.notifications.processors.camel.RetryCounterProcessor.CAMEL_GOOGLE_CHAT_RETRY_COUNTER;
import static com.redhat.cloud.notifications.processors.google.chat.GoogleChatRouteBuilder.GOOGLE_CHAT_INCOMING_ROUTE;
import static com.redhat.cloud.notifications.processors.google.chat.GoogleChatRouteBuilder.OUTGOING_ROUTE;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilder.REST_PATH;
import static com.redhat.cloud.notifications.processors.slack.SlackRouteBuilderTest.buildNotification;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.mockserver.model.HttpRequest.request;

@QuarkusTest
public class MetricsTest extends CamelMetricsTest {

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
