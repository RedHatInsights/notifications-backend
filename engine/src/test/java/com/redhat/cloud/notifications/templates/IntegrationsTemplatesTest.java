package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.INTEGRATION_FAILED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.CLIENT_ERROR_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.SERVER_ERROR_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.buildIntegrationDisabledAction;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class IntegrationsTemplatesTest {

    @Inject
    Environment environment;

    private final Integrations integrations = new Integrations();

    @Test
    void shouldSupportAllEventTypesWithInstantSubscriptionType() {
        assertTrue(integrations.isSupported(INTEGRATION_DISABLED_EVENT_TYPE, INSTANT));
        assertTrue(integrations.isSupported(INTEGRATION_FAILED_EVENT_TYPE, INSTANT));
    }

    @Test
    void shouldNotSupportAnyEventTypeWithDailySubscriptionType() {
        assertFalse(integrations.isSupported(INTEGRATION_DISABLED_EVENT_TYPE, DAILY));
        assertFalse(integrations.isSupported(INTEGRATION_FAILED_EVENT_TYPE, DAILY));
    }

    @Test
    void shouldNotSupportAnyEmailSubscriptionType() {
        assertFalse(integrations.isEmailSubscriptionSupported(INSTANT));
        assertFalse(integrations.isEmailSubscriptionSupported(DAILY));
    }

    @Test
    void shouldThrowWhenEmailSubscriptionTypeIsDailyOnGetTitle() {
        assertThrows(UnsupportedOperationException.class, () -> {
            integrations.getTitle(INTEGRATION_DISABLED_EVENT_TYPE, DAILY);
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            integrations.getTitle(INTEGRATION_FAILED_EVENT_TYPE, DAILY);
        });
    }

    @Test
    void shouldThrowWhenEmailSubscriptionTypeIsDailyOnGetBody() {
        assertThrows(UnsupportedOperationException.class, () -> {
            integrations.getBody(INTEGRATION_DISABLED_EVENT_TYPE, DAILY);
        });
        assertThrows(UnsupportedOperationException.class, () -> {
            integrations.getBody(INTEGRATION_FAILED_EVENT_TYPE, DAILY);
        });
    }

    @Test
    void testIntegrationDisabledTitle() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, CLIENT_ERROR_TYPE, 401, 1);
        String rendered = Integrations.Templates.integrationDisabledTitle()
                .data("action", action)
                .data("environment", environment)
                .render();
        assertTrue(rendered.endsWith("Integration '" + endpoint.getName() + "' was disabled"));
    }

    @Test
    void testIntegrationDisabledBodyWithClientError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, CLIENT_ERROR_TYPE, 401, 1);
        String rendered = Integrations.Templates.integrationDisabledBody()
                .data("action", action)
                .data("environment", environment)
                .render();
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
    }

    @Test
    void testIntegrationDisabledBodyWithServerError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, SERVER_ERROR_TYPE, -1, 2048);
        String rendered = Integrations.Templates.integrationDisabledBody()
                .data("action", action)
                .data("environment", environment)
                .render();
        assertTrue(rendered.contains("disabled because the remote endpoint responded 2048 times with a server error"));
    }

    private static Endpoint buildEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setName("Unreliable integration");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        return endpoint;
    }
}
