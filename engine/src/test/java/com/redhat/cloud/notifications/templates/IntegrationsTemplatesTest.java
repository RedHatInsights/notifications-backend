package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.ConnectorReceiver.INTEGRATION_FAILED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.CLIENT_ERROR_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.SERVER_ERROR_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.buildIntegrationDisabledAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class IntegrationsTemplatesTest extends EmailTemplatesInDbHelper {

    @Inject
    FeatureFlipper featureFlipper;

    @AfterEach
    void afterEach() {
        featureFlipper.setIntegrationsEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getBundle() {
        return "console";
    }

    @Override
    protected String getApp() {
        return "integrations";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(INTEGRATION_DISABLED_EVENT_TYPE, INTEGRATION_FAILED_EVENT_TYPE);
    }

    @Test
    void testIntegrationDisabledTitle() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, CLIENT_ERROR_TYPE, 401, 1);

        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(INTEGRATION_DISABLED_EVENT_TYPE, action);
            assertTrue(result.endsWith("Integration '" + endpoint.getName() + "' was disabled"));

            featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(INTEGRATION_DISABLED_EVENT_TYPE, action);
            assertEquals("Instant notification - Integrations - Console", result);
        });
    }

    @Test
    void testIntegrationFailedTitle() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        statelessSessionFactory.withSession(statelessSession -> {
            String rendered = generateEmailSubject(INTEGRATION_FAILED_EVENT_TYPE, action);
            assertEquals("Integration 'Failed integration' failed", rendered);

            featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
            migrate();
            rendered = generateEmailSubject(INTEGRATION_FAILED_EVENT_TYPE, action);
            assertEquals("Instant notification - Integrations - Console", rendered);
        });
    }

    @Test
    void testIntegrationFailedBody() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        statelessSessionFactory.withSession(statelessSession -> {
            String rendered = generateEmailBody(INTEGRATION_FAILED_EVENT_TYPE, action);
            assertTrue(rendered.contains("Integration 'Failed integration' failed with outcome"));

            featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
            migrate();
            rendered = generateEmailBody(INTEGRATION_FAILED_EVENT_TYPE, action);
            assertTrue(rendered.contains("Integration 'Failed integration' failed with outcome"));
            assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    void testIntegrationDisabledBodyWithClientError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, CLIENT_ERROR_TYPE, 401, 1);
        statelessSessionFactory.withSession(statelessSession -> {
            String rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
            assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));

            featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
            migrate();
            rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
            assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
            assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    void testIntegrationDisabledBodyWithServerError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, SERVER_ERROR_TYPE, -1, 2048);
        statelessSessionFactory.withSession(statelessSession -> {
            String rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
            assertTrue(rendered.contains("disabled because the remote endpoint responded 2048 times with a server error"));

            featureFlipper.setIntegrationsEmailTemplatesV2Enabled(true);
            migrate();
            rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
            assertTrue(rendered.contains("disabled because the remote endpoint responded 2048 times with a server error"));
            assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    private static Endpoint buildEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setName("Unreliable integration");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        return endpoint;
    }

}
