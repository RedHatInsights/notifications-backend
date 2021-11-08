package com.redhat.cloud.notifications.processors.webhook;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Multi;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class WebhookTest extends DbIsolatedTest {

    private static final long MAX_RETRY_ATTEMPTS = 4L;

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    WebhookTypeProcessor webhookTypeProcessor;

    private HttpRequest getMockHttpRequest(ExpectationResponseCallback verifyEmptyRequest) {
        HttpRequest postReq = new HttpRequest()
                .withPath("/foobar")
                .withMethod("POST");
        mockServerConfig.getMockServerClient()
                .withSecure(false)
                .when(postReq)
                .respond(verifyEmptyRequest);
        return postReq;
    }

    @Test
    void testWebhook() {
        String url = String.format("http://%s/foobar", mockServerConfig.getRunningAddress());

        final List<String> bodyRequests = new ArrayList<>();
        ExpectationResponseCallback verifyEmptyRequest = req -> {
            bodyRequests.add(req.getBodyAsString());
            return response().withStatusCode(200);
        };

        HttpRequest postReq = getMockHttpRequest(verifyEmptyRequest);

        Action webhookActionMessage = buildWebhookAction();
        Event event = new Event();
        event.setAction(webhookActionMessage);
        Endpoint ep = buildWebhookEndpoint(url);

        try {
            Multi<NotificationHistory> process = webhookTypeProcessor.process(event, List.of(ep));
            NotificationHistory history = process.collect().asList().await().indefinitely().get(0);
            assertTrue(history.isInvocationResult());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(postReq);
        }

        assertEquals(1, bodyRequests.size());
        JsonObject webhookInput = new JsonObject(bodyRequests.get(0));

        assertEquals("mybundle", webhookInput.getString("bundle"));
        assertEquals("WebhookTest", webhookInput.getString("application"));
        assertEquals("testWebhook", webhookInput.getString("event_type"));
        assertEquals("tenant", webhookInput.getString("account_id"));

        JsonObject webhookInputContext = webhookInput.getJsonObject("context");
        assertEquals("more", webhookInputContext.getString("free"));
        assertEquals(1, webhookInputContext.getInteger("format"));
        assertEquals("stuff", webhookInputContext.getString("here"));

        JsonArray webhookInputEvents = webhookInput.getJsonArray("events");
        assertEquals(2, webhookInputEvents.size());

        JsonObject webhookInputPayload1 = webhookInputEvents.getJsonObject(0).getJsonObject("payload");
        assertEquals("thing", webhookInputPayload1.getString("any"));
        assertEquals(1, webhookInputPayload1.getInteger("we"));
        assertEquals("here", webhookInputPayload1.getString("want"));

    }

    @Test
    void testRetryWithFinalSuccess() {
        testRetry(true);
    }

    @Test
    void testRetryWithFinalFailure() {
        testRetry(false);
    }

    private void testRetry(boolean shouldSucceedEventually) {
        String url = String.format("http://%s/foobar", mockServerConfig.getRunningAddress());

        AtomicInteger callsCounter = new AtomicInteger();
        ExpectationResponseCallback expectationResponseCallback = request -> {
            if (callsCounter.incrementAndGet() == MAX_RETRY_ATTEMPTS && shouldSucceedEventually) {
                return response().withStatusCode(200);
            } else {
                return response().withStatusCode(500);
            }
        };

        HttpRequest mockServerRequest = getMockHttpRequest(expectationResponseCallback);
        try {
            Action action = buildWebhookAction();
            Event event = new Event();
            event.setAction(action);
            Endpoint ep = buildWebhookEndpoint(url);
            Multi<NotificationHistory> process = webhookTypeProcessor.process(event, List.of(ep));
            NotificationHistory history = process.collect().asList().await().indefinitely().get(0);

            assertEquals(shouldSucceedEventually, history.isInvocationResult());
            assertEquals(MAX_RETRY_ATTEMPTS, callsCounter.get());
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(mockServerRequest);
        }
    }

    private static Action buildWebhookAction() {
        Action webhookActionMessage = new Action();
        webhookActionMessage.setBundle("mybundle");
        webhookActionMessage.setApplication("WebhookTest");
        webhookActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        webhookActionMessage.setEventType("testWebhook");
        webhookActionMessage.setAccountId("tenant");

        Map<String, Object> payload1 = new HashMap<>();
        payload1.put("any", "thing");
        payload1.put("we", 1);
        payload1.put("want", "here");

        Map<String, Object> context = new HashMap<>();
        context.put("free", "more");
        context.put("format", 1);
        context.put("here", "stuff");

        webhookActionMessage.setEvents(
                List.of(
                        com.redhat.cloud.notifications.ingress.Event
                                .newBuilder()
                                .setMetadataBuilder(Metadata.newBuilder())
                                .setPayload(payload1)
                                .build(),
                        com.redhat.cloud.notifications.ingress.Event
                                .newBuilder()
                                .setMetadataBuilder(Metadata.newBuilder())
                                .setPayload(new HashMap())
                                .build()
                )
        );

        webhookActionMessage.setContext(context);

        return webhookActionMessage;
    }

    private static Endpoint buildWebhookEndpoint(String url) {
        WebhookProperties properties = new WebhookProperties();
        properties.setMethod(HttpType.POST);
        properties.setUrl(url);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        return ep;
    }
}
