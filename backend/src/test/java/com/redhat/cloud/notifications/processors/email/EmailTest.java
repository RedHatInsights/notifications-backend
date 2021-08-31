package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.DbIsolatedTest;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.recipients.rbac.RbacServiceToService;
import com.redhat.cloud.notifications.recipients.rbac.RbacUser;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import com.redhat.cloud.notifications.templates.LocalDateTimeExtension;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Trigger;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockserver.model.HttpResponse.response;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
public class EmailTest extends DbIsolatedTest {
    @MockServerConfig
    MockServerClientConfig mockServerConfig;

    @Inject
    EmailSubscriptionTypeProcessor emailProcessor;

    @Inject
    ResourceHelpers helpers;

    @InjectMock
    @RestClient
    RbacServiceToService rbacServiceToService;

    static final String BOP_TOKEN = "test-token";
    static final String BOP_ENV = "unitTest";
    static final String BOP_CLIENT_ID = "test-client-id";

    @BeforeAll
    void init() {
        String url = String.format("http://%s/v1/sendEmails", mockServerConfig.getRunningAddress());
        System.setProperty("processor.email.bop_url", url);
        System.setProperty("processor.email.bop_apitoken", BOP_TOKEN);
        System.setProperty("processor.email.bop_env", BOP_ENV);
        System.setProperty("processor.email.bop_client_id", BOP_CLIENT_ID);
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
        mockGetUsers(8, false);

        final String tenant = "instant-email-tenant";
        final String[] usernames = {"username-1", "username-2", "username-4"};
        String bundle = "rhel";
        String application = "policies";

        for (String username : usernames) {
            helpers.subscribe(tenant, username, bundle, application, EmailSubscriptionType.INSTANT);
        }

        final List<String> bodyRequests = new ArrayList<>();

        ExpectationResponseCallback verifyEmptyRequest = req -> {
            assertEquals(BOP_TOKEN, req.getHeader(EmailSender.BOP_APITOKEN_HEADER).get(0));
            assertEquals(BOP_CLIENT_ID, req.getHeader(EmailSender.BOP_CLIENT_ID_HEADER).get(0));
            assertEquals(BOP_ENV, req.getHeader(EmailSender.BOP_ENV_HEADER).get(0));
            bodyRequests.add(req.getBodyAsString());
            return response().withStatusCode(200);
        };

        HttpRequest postReq = getMockHttpRequest(verifyEmptyRequest);

        Action emailActionMessage = TestHelpers.createPoliciesAction(tenant, bundle, application, "My test machine");

        Event event = new Event();
        event.setAction(emailActionMessage);

        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        try {
            Multi<NotificationHistory> process = emailProcessor.process(event, List.of(ep));
            NotificationHistory history = process.collect().asList().await().indefinitely().get(0);
            assertTrue(history.isInvocationResult());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(postReq);
        }

        assertEquals(3, bodyRequests.size());
        List<JsonObject> emailRequests = emailRequestIsOK(bodyRequests, usernames);

        for (int i = 0; i < usernames.length; ++i) {
            JsonObject body = emailRequests.get(i);
            JsonArray emails = body.getJsonArray("emails");
            assertNotNull(emails);
            assertEquals(1, emails.size());
            JsonObject firstEmail = emails.getJsonObject(0);
            JsonArray recipients = firstEmail.getJsonArray("recipients");
            assertEquals(1, recipients.size());
            assertEquals(usernames[i], recipients.getString(0));

            JsonArray bccList = firstEmail.getJsonArray("bccList");
            assertEquals(0, bccList.size());

            String bodyRequest = body.toString();

            assertTrue(bodyRequest.contains(TestHelpers.policyId1), "Body should contain policy id" + TestHelpers.policyId1);
            assertTrue(bodyRequest.contains(TestHelpers.policyName1), "Body should contain policy name" + TestHelpers.policyName1);

            assertTrue(bodyRequest.contains(TestHelpers.policyId2), "Body should contain policy id" + TestHelpers.policyId2);
            assertTrue(bodyRequest.contains(TestHelpers.policyName2), "Body should contain policy name" + TestHelpers.policyName2);

            // Display name
            assertTrue(bodyRequest.contains("My test machine"), "Body should contain the display_name");

            // Formatted date
            assertTrue(bodyRequest.contains("03 Aug 2020 15:22 UTC"));
        }
    }

    @Test
    void testEmailSubscriptionInstantWrongPayload() {
        mockGetUsers(8, false);
        final String tenant = "instant-email-tenant-wrong-payload";
        final String[] usernames = {"username-1", "username-2", "username-4"};
        String bundle = "rhel";
        String application = "policies";

        for (String username : usernames) {
            helpers.subscribe(tenant, username, bundle, application, EmailSubscriptionType.INSTANT);
        }

        final List<String> bodyRequests = new ArrayList<>();

        ExpectationResponseCallback verifyEmptyRequest = req -> {
            assertEquals(BOP_TOKEN, req.getHeader(EmailSender.BOP_APITOKEN_HEADER).get(0));
            assertEquals(BOP_CLIENT_ID, req.getHeader(EmailSender.BOP_CLIENT_ID_HEADER).get(0));
            assertEquals(BOP_ENV, req.getHeader(EmailSender.BOP_ENV_HEADER).get(0));
            bodyRequests.add(req.getBodyAsString());
            return response().withStatusCode(200);
        };

        HttpRequest postReq = getMockHttpRequest(verifyEmptyRequest);

        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(bundle);
        emailActionMessage.setApplication(application);
        emailActionMessage.setTimestamp(LocalDateTime.of(2020, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(TestHelpers.eventType);

        emailActionMessage.setContext(Map.of(
                "inventory_id-wrong", "host-01",
                "system_check_in-wrong", "2020-08-03T15:22:42.199046",
                "display_name-wrong", "My test machine",
                "tags-what?", List.of()
        ));
        emailActionMessage.setEvents(List.of(
                com.redhat.cloud.notifications.ingress.Event.newBuilder()
                        .setMetadataBuilder(Metadata.newBuilder())
                        .setPayload(Map.of(
                                "foo", "bar"
                        ))
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);

        Event event = new Event();
        event.setAction(emailActionMessage);

        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        try {
            Multi<NotificationHistory> process = emailProcessor.process(event, List.of(ep));
            // The processor returns a null history value but Multi does not support null values so the resulting Multi is empty.
            assertTrue(process.collect().asList().await().indefinitely().isEmpty());
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
    @Disabled
    void testEmailSubscriptionDaily() {
        mockGetUsers(8, false);
        final String tenant1 = "tenant1";
        final String tenant2 = "tenant2";
        final String noSubscribedUsersTenant = "tenant3";

        final String[] tenant1Usernames = {"username-1", "username-2", "username-3"};
        final String[] tenant2Usernames = {"username-4", "username-5"};
        final String[] noSubscribedUsersTenantTestUser = {"username-1"};
        final String bundle = "rhel";
        final String application = "policies";

        final String[] accountIds = {tenant1, tenant2, noSubscribedUsersTenant};
        // Daily emails now use the BehaviorGroup -> actions. Need to create the links
        UUID eventTypeId = helpers.createEventType(bundle, application, TestHelpers.eventType);
        for (String accountId : accountIds) {
            UUID endpointId = helpers.emailSubscriptionEndpointId(accountId, new EmailSubscriptionProperties());
            UUID bundleId = helpers.getBundleId(bundle);
            UUID behaviorGroupId = helpers.createBehaviorGroup(accountId, "test-behavior-group", bundleId).getId();

            helpers.updateEventTypeBehaviors(accountId, eventTypeId, Set.of(behaviorGroupId));
            helpers.updateBehaviorGroupActions(accountId, behaviorGroupId, List.of(endpointId));
        }

        for (String username : tenant1Usernames) {
            helpers.subscribe(tenant1, username, bundle, application, EmailSubscriptionType.DAILY);
        }

        for (String username : tenant2Usernames) {
            helpers.subscribe(tenant2, username, bundle, application, EmailSubscriptionType.DAILY);
        }

        for (String username : noSubscribedUsersTenantTestUser) {
            helpers.unsubscribe(noSubscribedUsersTenant, username, bundle, application, EmailSubscriptionType.DAILY);
        }

        final Instant nowPlus5HoursInstant = Instant.now().plus(Duration.ofHours(5));
        final LocalDateTime startTime = LocalDateTime.ofInstant(nowPlus5HoursInstant.minus(Duration.ofDays(1)), ZoneOffset.UTC);
        final LocalDateTime endTime = LocalDateTime.ofInstant(nowPlus5HoursInstant, ZoneOffset.UTC);

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
            assertEquals(BOP_TOKEN, req.getHeader(EmailSender.BOP_APITOKEN_HEADER).get(0));
            assertEquals(BOP_CLIENT_ID, req.getHeader(EmailSender.BOP_CLIENT_ID_HEADER).get(0));
            assertEquals(BOP_ENV, req.getHeader(EmailSender.BOP_ENV_HEADER).get(0));
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
            // 3 emails (3 users in tenant1), as no aggregation for tenant2
            assertEquals(3, bodyRequests.size());
            List<JsonObject> emails = emailRequestIsOK(bodyRequests, tenant1Usernames);
            for (JsonObject email: emails) {
                assertEquals(
                        String.format("%s - 3 policies triggered on 6 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                        email.getJsonArray("emails").getJsonObject(0).getString("subject")
                );
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-01"));
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-02"));
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-03"));
            }

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
            // 3 emails (3 users in tenant1), as no aggregation for tenant2
            assertEquals(3, bodyRequests.size());
            emails = emailRequestIsOK(bodyRequests, tenant1Usernames);
            for (JsonObject email: emails) {
                assertEquals(
                        String.format("%s - 3 policies triggered on 6 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                        email.getJsonArray("emails").getJsonObject(0).getString("subject")
                );
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-01"));
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-02"));
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-03"));
            }

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
            // 5 emails (3 from tenant1 and 2 from tenant2), as no user is subscribed for noSubscribedUsersTenant
            assertEquals(5, bodyRequests.size());

            // Emails could arrive in any order
            List<String> accountId1Emails = bodyRequests.stream().filter(s -> Arrays.stream(tenant1Usernames).anyMatch(s::contains)).collect(Collectors.toList());
            List<String> accountId2Emails = bodyRequests.stream().filter(s -> Arrays.stream(tenant2Usernames).anyMatch(s::contains)).collect(Collectors.toList());

            // First account
            emails = emailRequestIsOK(accountId1Emails, tenant1Usernames);
            for (JsonObject email: emails) {
                assertEquals(
                        String.format("%s - 3 policies triggered on 6 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                        email.getJsonArray("emails").getJsonObject(0).getString("subject")
                );
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-01"));
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-02"));
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-03"));
            }

            // Second account
            emails = emailRequestIsOK(accountId2Emails, tenant2Usernames);
            for (JsonObject email: emails) {
                assertEquals(
                        String.format("%s - 1 policy triggered on 3 unique systems", LocalDateTimeExtension.toStringFormat(startTime)),
                        email.getJsonArray("emails").getJsonObject(0).getString("subject")
                );
                assertTrue(email.getJsonArray("emails").getJsonObject(0).getString("body").contains("policyid-11"));
            }

            bodyRequests.clear();
            helpers.subscribe(noSubscribedUsersTenant, noSubscribedUsersTenantTestUser[0], bundle, application, EmailSubscriptionType.DAILY);
            emailProcessor.processDailyEmail(nowPlus5Hours);
            // 0 emails; previous aggregations were deleted in this step, even if no one was subscribed by that time
            assertEquals(0, bodyRequests.size());
            helpers.unsubscribe(noSubscribedUsersTenant, noSubscribedUsersTenantTestUser[0], bundle, application, EmailSubscriptionType.DAILY);

        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            mockServerConfig.getMockServerClient().clear(postReq);
        }

    }

    private String usernameOfRequest(String request, String[] users) {
        for (String user: users) {
            if (request.contains(user)) {
                return user;
            }
        }

        throw new RuntimeException("No username was found in the request");
    }

    private List<JsonObject> emailRequestIsOK(List<String> requests, String[] usersArray) {
        List<JsonObject> emailJson = new ArrayList<>();

        requests.sort(Comparator.comparing(s -> usernameOfRequest(s, usersArray)));
        List<String> userList = Arrays.stream(usersArray).sorted().collect(Collectors.toList());

        assertEquals(requests.size(), userList.size());
        for (int i = 0; i < userList.size(); ++i) {
            JsonObject email = new JsonObject(requests.get(i));
            JsonArray emails = email.getJsonArray("emails");
            assertNotNull(emails);
            assertEquals(1, emails.size());
            JsonObject firstEmail = emails.getJsonObject(0);
            JsonArray recipients = firstEmail.getJsonArray("recipients");
            assertEquals(1, recipients.size());
            assertEquals(userList.get(i), recipients.getString(0));

            JsonArray bccList = firstEmail.getJsonArray("bccList");
            assertEquals(0, bccList.size());

            emailJson.add(email);
        }

        return emailJson;
    }

    private void mockGetUsers(int elements, boolean adminsOnly) {
        MockedUserAnswer answer = new MockedUserAnswer(elements, adminsOnly);
        Mockito.when(rbacServiceToService.getUsers(
                Mockito.any(),
                Mockito.any(),
                Mockito.anyInt(),
                Mockito.anyInt()
        )).then(invocationOnMock -> answer.mockedUserAnswer(
                invocationOnMock.getArgument(2, Integer.class),
                invocationOnMock.getArgument(3, Integer.class),
                invocationOnMock.getArgument(1, Boolean.class)
        ));
    }

    class MockedUserAnswer {

        private final int expectedElements;
        private final boolean expectedAdminsOnly;

        MockedUserAnswer(int expectedElements, boolean expectedAdminsOnly) {
            this.expectedElements = expectedElements;
            this.expectedAdminsOnly = expectedAdminsOnly;
        }

        Uni<Page<RbacUser>> mockedUserAnswer(int offset, int limit, boolean adminsOnly) {

            Assertions.assertEquals(expectedAdminsOnly, adminsOnly);

            int bound = Math.min(offset + limit, expectedElements);

            List<RbacUser> users = new ArrayList<>();
            for (int i = offset; i < bound; ++i) {
                RbacUser user = new RbacUser();
                user.setActive(true);
                user.setUsername(String.format("username-%d", i));
                user.setEmail(String.format("username-%d@foobardotcom", i));
                user.setFirstName("foo");
                user.setLastName("bar");
                user.setOrgAdmin(false);
                users.add(user);
            }

            Page<RbacUser> usersPage = new Page<>();
            usersPage.setMeta(new Meta());
            usersPage.setLinks(new HashMap<>());
            usersPage.setData(users);

            return Uni.createFrom().item(usersPage);
        }
    }

}
