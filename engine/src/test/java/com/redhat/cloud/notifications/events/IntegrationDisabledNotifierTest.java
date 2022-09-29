package com.redhat.cloud.notifications.events;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.providers.connectors.InMemoryConnector;
import io.smallrye.reactive.messaging.providers.connectors.InMemorySink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Any;
import javax.inject.Inject;
import java.util.Map;
import java.util.UUID;

import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.EGRESS_CHANNEL;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.CLIENT_ERROR_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.CONSOLE_BUNDLE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.ENDPOINT_ID_PROPERTY;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.ENDPOINT_NAME_PROPERTY;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.ERRORS_COUNT_PROPERTY;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.ERROR_TYPE_PROPERTY;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATIONS_APP;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.SERVER_ERROR_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.STATUS_CODE_PROPERTY;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class IntegrationDisabledNotifierTest {

    @Inject
    IntegrationDisabledNotifier integrationDisabledNotifier;

    @Inject
    @Any
    InMemoryConnector inMemoryConnector;

    InMemorySink<String> egressChannel;

    @PostConstruct
    void postConstruct() {
        egressChannel = inMemoryConnector.sink(EGRESS_CHANNEL);
    }

    @BeforeEach
    @AfterEach
    void clearEgressChannel() {
        egressChannel.clear();
    }

    @Test
    void testClientError() {
        Endpoint endpoint = buildEndpoint();
        int errorStatusCode = 401;
        integrationDisabledNotifier.clientError(endpoint, errorStatusCode);
        checkReceivedAction(endpoint, CLIENT_ERROR_TYPE, errorStatusCode, 1);
    }

    @Test
    void testTooManyServerErrors() {
        Endpoint endpoint = buildEndpoint();
        integrationDisabledNotifier.tooManyServerErrors(endpoint, 10);
        checkReceivedAction(endpoint, SERVER_ERROR_TYPE, -1, 10);
    }

    private void checkReceivedAction(Endpoint endpoint, String expectedErrorType, int expectedStatusCode, int expectedErrorsCount) {
        await().until(() -> egressChannel.received().size() == 1);
        String payload = egressChannel.received().get(0).getPayload();
        Action action = Parser.decode(payload);

        assertNotNull(action.getId());
        assertEquals(CONSOLE_BUNDLE, action.getBundle());
        assertEquals(INTEGRATIONS_APP, action.getApplication());
        assertEquals(INTEGRATION_DISABLED_EVENT_TYPE, action.getEventType());
        assertEquals(endpoint.getOrgId(), action.getOrgId());
        assertTrue(action.getEvents().isEmpty());
        assertTrue(action.getRecipients().get(0).getOnlyAdmins());
        assertTrue(action.getRecipients().get(0).getIgnoreUserPreferences());

        Map<String, Object> contextProperties = action.getContext().getAdditionalProperties();
        assertEquals(expectedErrorType, contextProperties.get(ERROR_TYPE_PROPERTY));
        assertEquals(endpoint.getId().toString(), contextProperties.get(ENDPOINT_ID_PROPERTY));
        assertEquals(endpoint.getName(), contextProperties.get(ENDPOINT_NAME_PROPERTY));
        assertEquals(expectedErrorsCount, contextProperties.get(ERRORS_COUNT_PROPERTY));
        if (expectedStatusCode > 0) {
            assertEquals(expectedStatusCode, contextProperties.get(STATUS_CODE_PROPERTY));
        }
    }

    private static Endpoint buildEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setOrgId("org-id");
        endpoint.setName("My webhook");
        return endpoint;
    }
}
