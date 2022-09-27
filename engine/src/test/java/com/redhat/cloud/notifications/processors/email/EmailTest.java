package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.MockServerLifecycleManager;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.NotificationHistoryRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.NotificationHistory;
import com.redhat.cloud.notifications.recipients.itservice.ITUserService;
import com.redhat.cloud.notifications.recipients.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Authentication;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.PersonalInformation;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.List;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockserver.model.HttpResponse.response;

//@QuarkusTest
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@QuarkusTestResource(TestLifecycleManager.class)
public class EmailTest {

    @Inject
    EmailSubscriptionTypeProcessor emailProcessor;

    @Inject
    EntityManager entityManager;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @InjectMock
    @RestClient
    ITUserService itUserService;

    // InjectSpy allows us to update the fields via reflection (Inject does not)
    @InjectSpy
    EmailSender emailSender;

    @InjectMock
    NotificationHistoryRepository notificationHistoryRepository;

    static final String BOP_TOKEN = "test-token";
    static final String BOP_ENV = "unitTest";
    static final String BOP_CLIENT_ID = "test-client-id";

//    @BeforeAll
//    void init() {
//        String url = String.format("http://%s/v1/sendEmails", mockServerConfig.getRunningAddress());
//
//        updateField(emailSender, "bopUrl", url, EmailSender.class);
//        updateField(emailSender, "bopApiToken", BOP_TOKEN, EmailSender.class);
//        updateField(emailSender, "bopEnv", BOP_ENV, EmailSender.class);
//        updateField(emailSender, "bopClientId", BOP_CLIENT_ID, EmailSender.class);
//    }

    private HttpRequest getMockHttpRequest(ExpectationResponseCallback verifyEmptyRequest) {
        HttpRequest postReq = new HttpRequest()
                .withPath("/v1/sendEmails")
                .withMethod("POST");
        MockServerLifecycleManager.getClient()
                .withSecure(false)
                .when(postReq)
                .respond(verifyEmptyRequest);
        return postReq;
    }

    @Test
    @Disabled
    void testEmailSubscriptionInstant() {
        mockGetUsers(8);

        final String tenant = "instant-email-tenant";
        final String[] usernames = {"username-1", "username-2", "username-4"};
        String bundle = "rhel";
        String application = "policies";

        for (String username : usernames) {
            subscribe(DEFAULT_ORG_ID, username, bundle, application);
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
            statelessSessionFactory.withSession(statelessSession -> {
                emailProcessor.process(event, List.of(ep));
            });
            ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
            verify(notificationHistoryRepository, times(1)).createNotificationHistory(historyArgumentCaptor.capture());
            List<NotificationHistory> historyEntries = historyArgumentCaptor.getAllValues();

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
            MockServerLifecycleManager.getClient().clear(postReq);
        }
        clearSubscriptions();
    }

    @Test
    @Disabled
    void testEmailSubscriptionInstantWrongPayload() {
        mockGetUsers(8);
        final String tenant = "instant-email-tenant-wrong-payload";
        final String[] usernames = {"username-1", "username-2", "username-4"};
        String bundle = "rhel";
        String application = "policies";

        for (String username : usernames) {
            subscribe(DEFAULT_ORG_ID, username, bundle, application);
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
        emailActionMessage.setContext(
                new Context.ContextBuilder()
                        .withAdditionalProperty("inventory_id-wrong", "host-01")
                        .withAdditionalProperty("system_check_in-wrong", "2020-08-03T15:22:42.199046")
                        .withAdditionalProperty("display_name-wrong", "My test machine")
                        .withAdditionalProperty("tags-what?", List.of())
                        .build()
        );
        emailActionMessage.setEvents(List.of(
                new com.redhat.cloud.notifications.ingress.Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(new Payload.PayloadBuilder().withAdditionalProperty("foo", "bar").build())
                        .build()
        ));

        emailActionMessage.setAccountId(tenant);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

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
            statelessSessionFactory.withSession(statelessSession -> {
                emailProcessor.process(event, List.of(ep));
            });
            ArgumentCaptor<NotificationHistory> historyArgumentCaptor = ArgumentCaptor.forClass(NotificationHistory.class);
            verify(notificationHistoryRepository, times(0)).createNotificationHistory(historyArgumentCaptor.capture());
            List<NotificationHistory> historyEntries = historyArgumentCaptor.getAllValues();

            // The processor returns a null history value but Multi does not support null values so the resulting Multi is empty.
            assertTrue(historyEntries.isEmpty());

            // No email, invalid payload
            assertEquals(0, bodyRequests.size());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e);
        } finally {
            // Remove expectations
            MockServerLifecycleManager.getClient().clear(postReq);
        }
        clearSubscriptions();
    }

    private String usernameOfRequest(String request, String[] users) {
        for (String user : users) {
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

    private void mockGetUsers(int elements) {
        MockedUserAnswer answer = new MockedUserAnswer(elements);
        Mockito.when(itUserService.getUsers(Mockito.any(ITUserRequest.class)
        )).then(invocationOnMock -> answer.mockedUserAnswer());
    }

    static class MockedUserAnswer {

        private final int expectedElements;

        MockedUserAnswer(int expectedElements) {
            this.expectedElements = expectedElements;
        }

        List<ITUserResponse> mockedUserAnswer() {

            List<ITUserResponse> users = new ArrayList<>();
            for (int i = 0; i < expectedElements; ++i) {
                ITUserResponse user = new ITUserResponse();

                user.authentications = new ArrayList<>();
                user.authentications.add(new Authentication());
                user.authentications.get(0).principal = String.format("username-%d", i);

                com.redhat.cloud.notifications.recipients.itservice.pojo.response.Email email = new com.redhat.cloud.notifications.recipients.itservice.pojo.response.Email();
                email.address = String.format("username-%d@foobardotcom", i);
                user.accountRelationships = new ArrayList<>();
                user.accountRelationships.add(new AccountRelationship());
                user.accountRelationships.get(0).emails = List.of(email);

                user.personalInformation = new PersonalInformation();
                user.personalInformation.firstName = "foo";
                user.personalInformation.lastNames = "bar";

                users.add(user);
            }

            return users;
        }
    }

    @Transactional
    void subscribe(String orgId, String username, String bundleName, String applicationName) {
        String query = "INSERT INTO endpoint_email_subscriptions(org_id, user_id, application_id, subscription_type) " +
                "SELECT :orgId, :userId, a.id, :subscriptionType " +
                "FROM applications a, bundles b WHERE a.bundle_id = b.id AND a.name = :applicationName AND b.name = :bundleName " +
                "ON CONFLICT (org_id, user_id, application_id, subscription_type) DO NOTHING";
        entityManager.createNativeQuery(query)
                .setParameter("orgId", orgId)
                .setParameter("userId", username)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .setParameter("subscriptionType", INSTANT.name())
                .executeUpdate();
    }

    @Transactional
    void clearSubscriptions() {
        entityManager.createNativeQuery("DELETE FROM endpoint_email_subscriptions")
                .executeUpdate();
    }
}
