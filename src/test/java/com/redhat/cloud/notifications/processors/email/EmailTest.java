package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.models.EmailAttributes;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.mutiny.core.Vertx;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EmailTest {
    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    WebhookTypeProcessor webhookTypeProcessor;

    EmailTypeProcessor emailProcessor;

    @Inject
    Vertx vertx;

    @BeforeAll
    void init() {
        emailProcessor = new EmailTypeProcessor();
        emailProcessor.vertx = vertx;
        emailProcessor.webhookSender = webhookTypeProcessor;
        emailProcessor.bopApiToken = "test-token";
        emailProcessor.bopClientId = "emailTest";
        emailProcessor.bopEnv = "unitTest";
        emailProcessor.noReplyAddress = "no-reply@redhat.com";

        String url = String.format("http://%s/v1/sendEmails", mockServerConfig.getRunningAddress());
        emailProcessor.bopUrl = url;
    }

    private HttpRequest getMockHttpRequest(ExpectationResponseCallback verifyEmptyRequest) {
        HttpRequest postReq = new HttpRequest()
                .withPath("/v1/sendEmails")
                .withMethod("POST");
        mockServerConfig.getMockServerClient()
                .withSecure(false)
                .when(postReq)
                .respond(verifyEmptyRequest);
        return postReq;
    }

    @Test
    void testBOPEmailSerialization() {
        final List<String> bodyResponses = new ArrayList<>();

        ExpectationResponseCallback verifyEmptyRequest = req -> {
            assertEquals(emailProcessor.bopApiToken, req.getHeader(EmailTypeProcessor.BOP_APITOKEN_HEADER).get(0));
            assertEquals(emailProcessor.bopClientId, req.getHeader(EmailTypeProcessor.BOP_CLIENT_ID_HEADER).get(0));
            assertEquals(emailProcessor.bopEnv, req.getHeader(EmailTypeProcessor.BOP_ENV_HEADER).get(0));
            bodyResponses.add(req.getBodyAsString());
            return response().withStatusCode(200);
        };

        HttpRequest postReq = getMockHttpRequest(verifyEmptyRequest);

        // Read the input file and send it
        Action emailActionMessage = new Action();
        emailActionMessage.setApplication("EmailTest");
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventId(UUID.randomUUID().toString());
        emailActionMessage.setEventType("testBOPIntegration");
        emailActionMessage.setTags(new ArrayList<>());

        // TODO Modify this to match current email requirements
        Context context = new Context();
        context.setAccountId("tenant");
        Map<String, String> values = new HashMap<>();
        values.put("k", "v");
        values.put("k2", "v2");
        values.put("k3", "v");
        context.setMessage(values);
        emailActionMessage.setEvent(context);

        // TODO Test if necessary the emailAttributes
        EmailAttributes emailAttr = new EmailAttributes();
        emailAttr.setRecipients(Set.of("policies@redhat.com"));

        Endpoint ep = new Endpoint();
        ep.setType(Endpoint.EndpointType.EMAIL);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(emailAttr);

        Notification notif = new Notification(emailActionMessage, ep);

        try {
            Uni<NotificationHistory> process = emailProcessor.process(notif);
            NotificationHistory history = process.await().indefinitely();
            assertTrue(history.isInvocationResult());
        } catch (Exception e) {
            fail(e);
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(postReq);
        }

        assertEquals(1, bodyResponses.size());
        JsonObject body = new JsonObject(bodyResponses.get(0));
        JsonArray emails = body.getJsonArray("emails");
        assertNotNull(emails);
        assertEquals(1, emails.size());
        JsonObject firstEmail = emails.getJsonObject(0);
        JsonArray recipients = firstEmail.getJsonArray("recipients");
        assertEquals(1, recipients.size());
        assertEquals("no-reply@redhat.com", recipients.getString(0));

        JsonArray bccList = firstEmail.getJsonArray("bccList");
        assertEquals(emailAttr.getRecipients().size(), bccList.size());
        assertIterableEquals(emailAttr.getRecipients(), bccList.getList());

//        assertEquals("{\"emails\":[{\"subject\":null,\"body\":null,\"recipients\":[\"no-reply@redhat.com\"],\"ccList\":[],\"bccList\":[\"policies@redhat.com\"],\"bodyType\":\"html\"}]}", bodyResponses.get(0));
    }
}
