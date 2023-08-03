package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
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
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.buildIntegrationDisabledAction;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestIntegrationsTemplate extends IntegrationTemplatesInDbHelper {

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
    void testRenderedTemplateIntegrationDisabled() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, CLIENT_ERROR_TYPE, 401, 1);
        String result = generateDrawerTemplate(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertEquals("Integration <b>Unreliable integration</b> was disabled because the remote endpoint responded with an HTTP status code 401.", result);
    }

    @Test
    void testRenderedTemplateIntegrationFailed() {
        Action action = TestHelpers.createIntegrationsFailedAction();
        String result = generateDrawerTemplate(INTEGRATION_FAILED_EVENT_TYPE, action);
        assertEquals("Integration 'Failed integration' failed.", result);
    }

    private static Endpoint buildEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setName("Unreliable integration");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        return endpoint;
    }
}
