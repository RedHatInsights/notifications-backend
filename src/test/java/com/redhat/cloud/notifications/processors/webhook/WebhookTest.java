package com.redhat.cloud.notifications.processors.webhook;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
public class WebhookTest {

    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    WebhookTypeProcessor webhookTypeProcessor;

    @Inject
    ResourceHelpers helpers;

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
                        Event
                                .newBuilder()
                                .setMetadataBuilder(Metadata.newBuilder())
                                .setPayload(payload1)
                                .build(),
                        Event
                                .newBuilder()
                                .setMetadataBuilder(Metadata.newBuilder())
                                .setPayload(new HashMap())
                                .build()
                )
        );

        webhookActionMessage.setContext(context);

        WebhookAttributes attributes = new WebhookAttributes();
        attributes.setMethod(HttpType.POST);
        attributes.setUrl(url);

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.WEBHOOK);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(attributes);

        Notification notif = new Notification(webhookActionMessage, ep);
        try {
            Uni<NotificationHistory> process = webhookTypeProcessor.process(notif);
            NotificationHistory history = process.await().indefinitely();
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

}
