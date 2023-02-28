package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.processors.webhooks.WebhookTypeProcessor;
import com.redhat.cloud.notifications.recipients.itservice.ITUserService;
import com.redhat.cloud.notifications.recipients.itservice.pojo.request.ITUserRequest;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.AccountRelationship;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.Authentication;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.ITUserResponse;
import com.redhat.cloud.notifications.recipients.itservice.pojo.response.PersonalInformation;
import com.redhat.cloud.notifications.templates.EmailTemplateFactory;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@QuarkusTestResource(TestLifecycleManager.class)
@TestProfile(EmailSubscriptionTypeProcessorWithMigratedTemplateTest.EnvVarTestSetup.class)
public class EmailSubscriptionTypeProcessorWithMigratedTemplateTest {

    @Inject
    EmailSubscriptionTypeProcessor emailProcessor;

    @Inject
    EntityManager entityManager;

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @InjectMock
    @RestClient
    ITUserService itUserService;

    @InjectSpy
    EmailTemplateFactory emailTemplateFactory;

    @InjectMock
    WebhookTypeProcessor webhookSender;

    @InjectSpy
    TemplateRepository templateRepository;

    @Inject
    FeatureFlipper featureFlipper;

    String commonTest() {
        mockGetUsers(1);

        final String tenant = "instant-email-tenant";
        final String username = "username-0";
        String bundle = "rhel";
        String application = "policies";

        subscribe(DEFAULT_ORG_ID, username, bundle, application);

        final List<String> bodyRequests = new ArrayList<>();

        doAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            bodyRequests.add(String.valueOf(args[3])); // The fourth argument is email Payload
            return null;
        }).when(webhookSender)
            .doHttpRequest(any(), any(), any(), any(), anyString(), anyString(), anyBoolean());

        Action emailActionMessage = TestHelpers.createPoliciesAction(tenant, bundle, application, "My test machine");
        List<EventType> events =  getEventTypes();

        Event event = new Event();
        event.setEventType(events.get(0));
        event.setAction(emailActionMessage);

        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();

        Endpoint ep = new Endpoint();
        ep.setType(EndpointType.EMAIL_SUBSCRIPTION);
        ep.setName("positive feeling");
        ep.setDescription("needle in the haystack");
        ep.setEnabled(true);
        ep.setProperties(properties);

        statelessSessionFactory.withSession(statelessSession -> {
            emailProcessor.process(event, List.of(ep));
        });

        assertEquals(1, bodyRequests.size());
        JsonObject email = new JsonObject(bodyRequests.get(0));
        String bodyRequest = email.getJsonArray("emails").getJsonObject(0).getString("body");

        assertTrue(bodyRequest.contains(TestHelpers.policyId1), "Body should contain policy id" + TestHelpers.policyId1);
        assertTrue(bodyRequest.contains(TestHelpers.policyName1), "Body should contain policy name" + TestHelpers.policyName1);

        assertTrue(bodyRequest.contains(TestHelpers.policyId2), "Body should contain policy id" + TestHelpers.policyId2);
        assertTrue(bodyRequest.contains(TestHelpers.policyName2), "Body should contain policy name" + TestHelpers.policyName2);

        // Display name
        assertTrue(bodyRequest.contains("My test machine"), "Body should contain the display_name");

        assertTrue(bodyRequest.contains(TestHelpers.HCC_LOGO_TARGET));

        return bodyRequest;
    }

    @Test
    void testEmailSubscriptionInstantFromFileSystem() {
        featureFlipper.setUseTemplatesFromDb(false);
        commonTest();
        verify(emailTemplateFactory, times(2)).get(anyString(), anyString());
        verify(templateRepository, times(0)).findInstantEmailTemplate(any(UUID.class));
    }

    @Test
    void testEmailSubscriptionInstantFromDatabase() {
        featureFlipper.setUseTemplatesFromDb(true);
        String renderedEmailFromDb = commonTest();
        verify(emailTemplateFactory, times(0)).get(anyString(), anyString());
        verify(templateRepository, times(1)).findInstantEmailTemplate(any(UUID.class));

        featureFlipper.setUseTemplatesFromDb(false);
        String renderedEmailFromFs = commonTest();
        assertEquals(renderedEmailFromDb, renderedEmailFromFs);
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

    List<EventType> getEventTypes() {
        String query = "From EventType";
        return entityManager.createQuery(query, EventType.class)
            .getResultList();
    }

    public static class EnvVarTestSetup implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("notifications.use-templates-from-db", "true",
                "notifications.inject-email-templates-to-db-on-startup.enabled", "true",
                "notifications.use-policies-email-templates-v2.enabled", "true"
                );
        }
    }
}
