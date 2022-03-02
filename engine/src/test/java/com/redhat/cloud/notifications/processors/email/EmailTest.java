package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MockServerClientConfig;
import com.redhat.cloud.notifications.MockServerConfig;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.recipients.rbac.RbacServiceToService;
import com.redhat.cloud.notifications.recipients.rbac.RbacUser;
import com.redhat.cloud.notifications.routers.models.Meta;
import com.redhat.cloud.notifications.routers.models.Page;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.mockserver.mock.action.ExpectationResponseCallback;
import org.mockserver.model.HttpRequest;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.ReflectionHelper.updateField;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
    EmailSubscriptionTypeProcessor emailProcessor;

    @Inject
    EntityManager entityManager;

    @InjectMock
    @RestClient
    RbacServiceToService rbacServiceToService;

    // InjectSpy allows us to update the fields via reflection (Inject does not)
    @InjectSpy
    EmailSender emailSender;

    static final String BOP_TOKEN = "test-token";
    static final String BOP_ENV = "unitTest";
    static final String BOP_CLIENT_ID = "test-client-id";

    @BeforeAll
    void init() {
        String url = String.format("http://%s/v1/sendEmails", mockServerConfig.getRunningAddress());

        updateField(emailSender, "bopUrl", url, EmailSender.class);
        updateField(emailSender, "bopApiToken", BOP_TOKEN, EmailSender.class);
        updateField(emailSender, "bopEnv", BOP_ENV, EmailSender.class);
        updateField(emailSender, "bopClientId", BOP_CLIENT_ID, EmailSender.class);
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
            subscribe(tenant, username, bundle, application);
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
            List<NotificationHistory> historyEntries = emailProcessor.process(event, List.of(ep));

            NotificationHistory history = historyEntries.get(0);
            assertTrue(history.isInvocationResult());

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
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(postReq);
        }
        clearSubscriptions();
    }

    @Test
    void testEmailSubscriptionInstantWrongPayload() {
        mockGetUsers(8, false);
        final String tenant = "instant-email-tenant-wrong-payload";
        final String[] usernames = {"username-1", "username-2", "username-4"};
        String bundle = "rhel";
        String application = "policies";

        for (String username : usernames) {
            subscribe(tenant, username, bundle, application);
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
        emailActionMessage.setRecipients(List.of());
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
            List<NotificationHistory> historyEntries = emailProcessor.process(event, List.of(ep));

            // The processor returns a null history value but Multi does not support null values so the resulting Multi is empty.
            assertTrue(historyEntries.isEmpty());

            // No email, invalid payload
            assertEquals(0, bodyRequests.size());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            mockServerConfig.getMockServerClient().clear(postReq);
        }
        clearSubscriptions();
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

        Page<RbacUser> mockedUserAnswer(int offset, int limit, boolean adminsOnly) {

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

            return usersPage;
        }
    }

    @Transactional
    boolean subscribe(String accountNumber, String username, String bundleName, String applicationName) {
        String query = "INSERT INTO endpoint_email_subscriptions(account_id, user_id, application_id, subscription_type) " +
                "SELECT :accountId, :userId, a.id, :subscriptionType " +
                "FROM applications a, bundles b WHERE a.bundle_id = b.id AND a.name = :applicationName AND b.name = :bundleName " +
                "ON CONFLICT (account_id, user_id, application_id, subscription_type) DO NOTHING";
        entityManager.createNativeQuery(query)
                .setParameter("accountId", accountNumber)
                .setParameter("userId", username)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", INSTANT.name())
                .executeUpdate();
        return true;
    }

    @Transactional
    void clearSubscriptions() {
        entityManager.createNativeQuery("DELETE FROM endpoint_email_subscriptions")
                .executeUpdate();
    }
}
