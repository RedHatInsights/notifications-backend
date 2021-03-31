package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.EmailAggregationResources;
import com.redhat.cloud.notifications.db.EndpointEmailSubscriptionResources;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionAttributes;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Notification;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.templates.LocalDateTimeExtension;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Trigger;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Inject
    EmailAggregationResources emailAggregationResources;

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
        emailProcessor.emailAggregationResources = emailAggregationResources;
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

        final String tenant = "instant-email-tenant";
        final String[] usernames = {"foo", "bar", "admin"};
        String bundle = "rhel";
        String application = "policies";

        for (String username : usernames) {
            helpers.createSubscription(tenant, username, bundle, application, EmailSubscriptionType.INSTANT);
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
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        // Disabling event id until we need it
        // emailActionMessage.setEventId(UUID.randomUUID().toString());
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        Map<String, String> triggers = new HashMap<>();
        triggers.put("abcd-efghi-jkl-lmn", "Foobar");
        triggers.put("0123-456-789-5721f", "Latest foo is installed");

        Map<String, Object> payload = new HashMap<>();
        payload.put("triggers", triggers);
        payload.put("display_name", "My test machine");
        payload.put("system_check_in", "2020-08-03T15:22:42.199046");
        payload.put("policy_id", "0123-456-789-5721f");
        payload.put("policy_name", "not-used-name");
        payload.put("policy_description", "not-used-desc");
        payload.put("policy_condition", "not-used-condition");
        payload.put("insights_id", "host-01");
        payload.put("tags", new JsonArray());

        emailActionMessage.setPayload(payload);
        emailActionMessage.setAccountId(tenant);

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

        // Formatted date
        assertTrue(bodyRequest.contains("03 Aug 2020 15:22 UTC"));
    }

    @Test
    void testEmailSubscriptionInstantWrongPayload() {

        final String tenant = "instant-email-tenant-wrong-payload";
        final String[] usernames = {"foo", "bar", "admin"};
        String bundle = "rhel";
        String application = "policies";

        for (String username : usernames) {
            helpers.createSubscription(tenant, username, bundle, application, EmailSubscriptionType.INSTANT);
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
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        // Disabling event id until we need it
        // emailActionMessage.setEventId(UUID.randomUUID().toString());
        emailActionMessage.setEventType("testEmailSubscriptionInstant");

        // No triggers (currently required by instant email)
        Map<String, Object> payload = new HashMap<>();
        payload.put("display_name", "My test machine");
        payload.put("system_check_in", "2020-08-03T15:22:42.199046");
        payload.put("policy_id", "0123-456-789-5721f");
        payload.put("policy_name", "not-used-name");
        payload.put("policy_description", "not-used-desc");
        payload.put("policy_condition", "not-used-condition");
        payload.put("insights_id", "host-01");
        payload.put("tags", new JsonArray());

        emailActionMessage.setPayload(payload);
        emailActionMessage.setAccountId(tenant);

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
            assertNull(history);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(postReq);
        }

        // No email, invalid payload
        assertEquals(0, bodyRequests.size());
    }

    @Test
    void testEmailSubscriptionDaily() {
        final String tenant1 = "tenant1";
        final String tenant2 = "tenant2";
        final String noSubscribedUsersTenant = "tenant3";

        final String[] tenant1Usernames = {"foo", "bar", "admin"};
        final String[] tenant2Usernames = {"baz", "bar"};
        final String[] noSubscribedUsersTenantTestUser = {"test"};
        final String bundle = "insights";
        final String application = "policies";

        for (String username : tenant1Usernames) {
            helpers.createSubscription(tenant1, username, bundle, application, EmailSubscriptionType.DAILY);
        }

        for (String username : tenant2Usernames) {
            helpers.createSubscription(tenant2, username, bundle, application, EmailSubscriptionType.DAILY);
        }

        for (String username : noSubscribedUsersTenantTestUser) {
            helpers.removeSubscription(noSubscribedUsersTenant, username, bundle, application, EmailSubscriptionType.DAILY);
        }

        final Instant nowPlus5HoursInstant = Instant.now().plus(Duration.ofHours(5));
        final LocalDateTime startTime = LocalDateTime.ofInstant(nowPlus5HoursInstant.minus(Duration.ofDays(1)), ZoneId.systemDefault());
        final LocalDateTime endTime = LocalDateTime.ofInstant(nowPlus5HoursInstant, ZoneId.systemDefault());

        ScheduledExecution nowPlus5Hours = new ScheduledExecution() {
            @Override
            public Trigger getTrigger() {
                return null;
            }

            @Override
            public Instant getFireTime() {
                return null;
            }

            @Override
            public Instant getScheduledFireTime() {
                return nowPlus5HoursInstant;
            }
        };

        final List<String> bodyRequests = new ArrayList<>();

        ExpectationResponseCallback verifyEmptyRequest = req -> {
            assertEquals(emailProcessor.bopApiToken, req.getHeader(EmailSubscriptionTypeProcessor.BOP_APITOKEN_HEADER).get(0));
            assertEquals(emailProcessor.bopClientId, req.getHeader(EmailSubscriptionTypeProcessor.BOP_CLIENT_ID_HEADER).get(0));
            assertEquals(emailProcessor.bopEnv, req.getHeader(EmailSubscriptionTypeProcessor.BOP_ENV_HEADER).get(0));
            bodyRequests.add(req.getBodyAsString());
            return response().withStatusCode(200);
        };

        HttpRequest postReq = getMockHttpRequest(verifyEmptyRequest);

        try {
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-01");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-02", "hostid-02");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-03", "hostid-03");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-04");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-05");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-06");
            emailProcessor.processDailyEmail(nowPlus5Hours);
            // Only 1 email, as no aggregation for tenant2
            assertEquals(1, bodyRequests.size());
            JsonObject email = emailRequestIsOK(bodyRequests.get(0), tenant1Usernames);
            assertEquals(
                    String.format("%s - 3 policies triggered on 6 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                    email.getJsonArray("emails").getJsonObject(0).getString("subject")
            );
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-01"));
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-02"));
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-03"));

            bodyRequests.clear();

            // applications without template or aggregations do not break the process
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-01");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-02", "hostid-02");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-03", "hostid-03");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-04");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-05");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-06");
            helpers.addEmailAggregation(tenant1, bundle, "unknown-application", "policyid-01", "hostid-06");
            helpers.addEmailAggregation(tenant1, "unknown-bundle", application, "policyid-01", "hostid-06");
            helpers.addEmailAggregation(tenant1, "unknown-bundle", "unknown-application", "policyid-01", "hostid-06");
            emailProcessor.processDailyEmail(nowPlus5Hours);
            // Only 1 email, as no aggregation for tenant2
            assertEquals(1, bodyRequests.size());
            email = emailRequestIsOK(bodyRequests.get(0), tenant1Usernames);
            assertEquals(
                    String.format("%s - 3 policies triggered on 6 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                    email.getJsonArray("emails").getJsonObject(0).getString("subject")
            );
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-01"));
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-02"));
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-03"));

            bodyRequests.clear();

            emailProcessor.processDailyEmail(nowPlus5Hours);
            // 0 emails; previous aggregations were deleted in this step
            assertEquals(0, bodyRequests.size());

            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-01");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-02", "hostid-02");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-03", "hostid-03");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-04");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-05");
            helpers.addEmailAggregation(tenant1, bundle, application, "policyid-01", "hostid-06");

            helpers.addEmailAggregation(tenant2, bundle, application, "policyid-11", "hostid-11");
            helpers.addEmailAggregation(tenant2, bundle, application, "policyid-11", "hostid-15");
            helpers.addEmailAggregation(tenant2, bundle, application, "policyid-11", "hostid-16");

            helpers.addEmailAggregation(noSubscribedUsersTenant, bundle, application, "policyid-21", "hostid-21");
            helpers.addEmailAggregation(noSubscribedUsersTenant, bundle, application, "policyid-21", "hostid-25");
            helpers.addEmailAggregation(noSubscribedUsersTenant, bundle, application, "policyid-21", "hostid-26");

            emailProcessor.processDailyEmail(nowPlus5Hours);
            // 2 email, as no user is subscribed for noSubscribedUsersTenant
            assertEquals(2, bodyRequests.size());

            // Emails could arrive in any order
            int firstEmailIndex = 0;
            int secondEmailIndex = 1;
            // Only the tenant1 has this user
            if (bodyRequests.get(1).contains("admin")) {
                firstEmailIndex = 1;
                secondEmailIndex = 0;
            }

            // First email
            email = emailRequestIsOK(bodyRequests.get(firstEmailIndex), tenant1Usernames);
            assertEquals(
                    String.format("%s - 3 policies triggered on 6 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                    email.getJsonArray("emails").getJsonObject(0).getString("subject")
            );
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-01"));
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-02"));
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-03"));

            // Second email
            email = emailRequestIsOK(bodyRequests.get(secondEmailIndex), tenant2Usernames);
            assertEquals(
                    String.format("%s - 1 policy triggered on 3 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                    email.getJsonArray("emails").getJsonObject(0).getString("subject")
            );
            assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-11"));
            bodyRequests.clear();

            helpers.createSubscription(noSubscribedUsersTenant, noSubscribedUsersTenantTestUser[0], bundle, application, EmailSubscriptionType.DAILY);
            emailProcessor.processDailyEmail(nowPlus5Hours);
            // 0 emails; previous aggregations were deleted in this step, even if no one was subscribed by that time
            assertEquals(0, bodyRequests.size());
            helpers.removeSubscription(noSubscribedUsersTenant, noSubscribedUsersTenantTestUser[0], bundle, application, EmailSubscriptionType.DAILY);

        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            mockServerConfig.getMockServerClient().clear(postReq);
        }

    }

    private JsonObject emailRequestIsOK(String request, String[] users) {
        JsonObject email = new JsonObject(request);
        JsonArray emails = email.getJsonArray("emails");
        assertNotNull(emails);
        assertEquals(1, emails.size());
        JsonObject firstEmail = emails.getJsonObject(0);
        JsonArray recipients = firstEmail.getJsonArray("recipients");
        assertEquals(1, recipients.size());
        assertEquals("no-reply@redhat.com", recipients.getString(0));

        JsonArray bccList = firstEmail.getJsonArray("bccList");
        assertEquals(users.length, bccList.size());

        List<String> sortedUsernames = Arrays.asList(users);
        sortedUsernames.sort(Comparator.naturalOrder());

        List<String> sortedBccList = new ArrayList<String>(bccList.getList());
        sortedBccList.sort(Comparator.naturalOrder());

        assertIterableEquals(sortedUsernames, sortedBccList);

        return email;
    }
}
