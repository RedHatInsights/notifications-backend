package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Tag;
import com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.EmailSubscriptionAttributes;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Endpoint.EndpointType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import io.quarkus.test.common.QuarkusTestResource;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailTest {
    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    WebhookTypeProcessor webhookTypeProcessor;

    EmailSubscriptionTypeProcessor emailProcessor;

    @Inject
    ResourceHelpers helpers;

    @Inject
    Vertx vertx;

    @Inject
    EndpointEmailSubscriptionResources subscriptionResources;

    @BeforeAll
    void init() {
        emailProcessor = new EmailSubscriptionTypeProcessor();
        emailProcessor.vertx = vertx;
        emailProcessor.webhookSender = webhookTypeProcessor;
        emailProcessor.subscriptionResources = subscriptionResources;
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
    void testEmailSubscriptionInstant() {

        final String tenant = "tenant";
        final String[] usernames = {"foo", "bar", "admin"};

        for (String username : usernames) {
            helpers.createSubscription(tenant, username, EmailSubscriptionType.INSTANT);
        }

        final List<String> bodyRequests = new ArrayList<>();

        ExpectationResponseCallback verifyEmptyRequest = req -> {
            assertEquals(emailProcessor.bopApiToken, req.getHeader(EmailSubscriptionTypeProcessor.BOP_APITOKEN_HEADER).get(0));
            assertEquals(emailProcessor.bopClientId, req.getHeader(EmailSubscriptionTypeProcessor.BOP_CLIENT_ID_HEADER).get(0));
            assertEquals(emailProcessor.bopEnv, req.getHeader(EmailSubscriptionTypeProcessor.BOP_ENV_HEADER).get(0));
            bodyRequests.add(req.getBodyAsString());
            return response().withStatusCode(200);
        };

        HttpRequest postReq = getMockHttpRequest(verifyEmptyRequest);

        Action emailActionMessage = new Action();
        emailActionMessage.setApplication("EmailTest");
        emailActionMessage.setTimestamp(LocalDateTime.now());
        emailActionMessage.setEventId(UUID.randomUUID().toString());
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        List<Tag> tags = new ArrayList<>();
        tags.add(new Tag("display_name", "My test machine"));

        emailActionMessage.setTags(tags);

        Map<String, String> triggers = new HashMap<>();
        triggers.put("abcd-efghi-jkl-lmn", "Foobar");
        triggers.put("0123-456-789-5721f", "Latest foo is installed");

        Map<String, Object> params = new HashMap<>();
        params.put("triggers", triggers);

        emailActionMessage.setParams(params);

        // TODO Modify this to match current email requirements
        Context context = new Context();
        context.setAccountId("tenant");
        Map<String, String> values = new HashMap<>();
        values.put("k", "v");
        values.put("k2", "v2");
        values.put("k3", "v");
        context.setMessage(values);
        emailActionMessage.setEvent(context);

        EmailSubscriptionAttributes emailAttr = new EmailSubscriptionAttributes();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
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
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(postReq);
        }

        assertEquals(1, bodyRequests.size());
        JsonObject body = new JsonObject(bodyRequests.get(0));
        JsonArray emails = body.getJsonArray("emails");
        assertNotNull(emails);
        assertEquals(1, emails.size());
        JsonObject firstEmail = emails.getJsonObject(0);
        JsonArray recipients = firstEmail.getJsonArray("recipients");
        assertEquals(1, recipients.size());
        assertEquals("no-reply@redhat.com", recipients.getString(0));

        JsonArray bccList = firstEmail.getJsonArray("bccList");
        assertEquals(usernames.length, bccList.size());

        List<String> sortedUsernames = Arrays.asList(usernames);
        sortedUsernames.sort(Comparator.naturalOrder());

        List<String> sortedBccList = new ArrayList<String>(bccList.getList());
        sortedBccList.sort(Comparator.naturalOrder());

        assertIterableEquals(sortedUsernames, sortedBccList);

        String bodyRequest = bodyRequests.get(0);

        for (String key : triggers.keySet()) {
            String value = triggers.get(key);
            assertTrue(bodyRequest.contains(key), "Body should contain trigger key" + key);
            assertTrue(bodyRequest.contains(value), "Body should contain trigger value" + value);
        }

        // Display name
        assertTrue(bodyRequest.contains("My test machine"), "Body should contain the display_name");
    }
}
