package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
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
        String result = generateEmailSubject(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertEquals("Instant notification - Integrations - Console", result);
    }

    @Test
    void testIntegrationFailedTitle() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        String rendered = generateEmailSubject(INTEGRATION_FAILED_EVENT_TYPE, action);
        assertEquals("Instant notification - Integrations - Console", rendered);
    }

    @Test
    void testIntegrationFailedBody() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        String rendered = generateEmailBody(INTEGRATION_FAILED_EVENT_TYPE, action);
        assertTrue(rendered.contains("Integration 'Failed integration' failed with outcome"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithClientError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, CLIENT_ERROR_TYPE, 401, 1);
        String rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithServerError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, SERVER_ERROR_TYPE, -1, 2048);
        String rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
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

}
