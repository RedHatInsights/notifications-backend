package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.Environment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class IntegrationsTemplatesTest {

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Integrations integrations;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setIntegrationsEmailTemplatesV2Enabled(false);
    }

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
        String rendered = generateEmail(integrations.getTitle(INTEGRATION_DISABLED_EVENT_TYPE, INSTANT), action);
        assertTrue(rendered.endsWith("Integration '" + endpoint.getName() + "' was disabled"));

        // test template V2
        featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
        rendered = generateEmail(integrations.getTitle(INTEGRATION_DISABLED_EVENT_TYPE, INSTANT), action);
        assertEquals("Instant notification - Integrations - Console", rendered);
    }

    @Test
    void testIntegrationFailedTitle() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        String rendered = generateEmail(integrations.getTitle(INTEGRATION_FAILED_EVENT_TYPE, INSTANT), action);
        assertEquals("Integration 'Failed integration' failed", rendered);

        // test template V2
        featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
        rendered = generateEmail(integrations.getTitle(INTEGRATION_FAILED_EVENT_TYPE, INSTANT), action);
        assertEquals("Instant notification - Integrations - Console", rendered);
    }

    @Test
    void testIntegrationBodyTitle() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        String rendered = generateEmail(integrations.getBody(INTEGRATION_FAILED_EVENT_TYPE, INSTANT), action);
        assertTrue(rendered.contains("Integration 'Failed integration' failed with outcome"));

        // test template V2
        featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
        rendered = generateEmail(integrations.getBody(INTEGRATION_FAILED_EVENT_TYPE, INSTANT), action);
        assertTrue(rendered.contains("Integration 'Failed integration' failed with outcome"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithClientError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, CLIENT_ERROR_TYPE, 401, 1);
        String rendered = generateEmail(integrations.getBody(INTEGRATION_DISABLED_EVENT_TYPE, INSTANT), action);
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));

        // test template V2
        featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
        rendered = generateEmail(integrations.getBody(INTEGRATION_DISABLED_EVENT_TYPE, INSTANT), action);
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithServerError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, SERVER_ERROR_TYPE, -1, 2048);
        String rendered = generateEmail(integrations.getBody(INTEGRATION_DISABLED_EVENT_TYPE, INSTANT), action);
        assertTrue(rendered.contains("disabled because the remote endpoint responded 2048 times with a server error"));

        // test template V2
        featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
        rendered = generateEmail(integrations.getBody(INTEGRATION_DISABLED_EVENT_TYPE, INSTANT), action);
        System.out.println(rendered);
        assertTrue(rendered.contains("disabled because the remote endpoint responded 2048 times with a server error"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private static Endpoint buildEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setName("Unreliable integration");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        return endpoint;
    }

    private String generateEmail(TemplateInstance template, Action action) {
        return template
            .data("action", action)
            .data("environment", environment)
            .render();
    }
}
