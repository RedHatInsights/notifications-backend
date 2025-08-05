package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.events.GeneralCommunicationsHelper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.events.HttpErrorType.HTTP_4XX;
import static com.redhat.cloud.notifications.events.HttpErrorType.HTTP_5XX;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.buildIntegrationDisabledAction;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class IntegrationsTemplatesTest extends EmailTemplatesRendererHelper {

    @Override
    protected String getBundle() {
        return "console";
    }

    @Override
    protected String getBundleDisplayName() {
        return "Console";
    }

    @Override
    protected String getApp() {
        return "integrations";
    }

    @Override
    protected String getAppDisplayName() {
        return "Integrations";
    }

    @Test
    void testIntegrationDisabledTitle() {
        Endpoint endpoint = buildEndpoint();
        eventTypeDisplayName = "Integration disabled";
        Action action = buildIntegrationDisabledAction(endpoint, HTTP_4XX, 401, 1);
        String result = generateEmailSubject(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertEquals("Instant notification - Integration disabled - Integrations - Console", result);
    }

    @Test
    void testIntegrationDisabledBodyWithClientError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, HTTP_4XX, 401, 1);
        String rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithServerError() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, HTTP_5XX, -1, 2048);
        String rendered = generateEmailBody(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertTrue(rendered.contains("disabled because the connection couldn't be established with the remote endpoint, or it responded too many times with a server error (HTTP status code 5xx) after 2048 attempts"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    /**
     * Tests that the general communication email works as expected.
     */
    @Test
    void testGeneralCommunication() {
        final String orgId = "12345";
        final List<String> integrationNames = List.of(
            "Integration one",
            "Another integration",
            "Third integration"
        );

        final Action action = GeneralCommunicationsHelper.createGeneralCommunicationAction(orgId, new CompositeEndpointType(CAMEL, "teams"), integrationNames);
        final String rendered = generateEmailBody(GeneralCommunicationsHelper.GENERAL_COMMUNICATIONS_EVENT_TYPE, action);
        assertTrue(rendered.contains("We are contacting you because you have the following Microsoft Teams integrations set up for your organization in the"));
        assertTrue(rendered.contains("Integration one"));
        assertTrue(rendered.contains("Another integration"));
        assertTrue(rendered.contains("Third integration"));
        assertTrue(rendered.contains("retiring Office 365 connectors in favor of Power Automate workflows"));
        assertTrue(rendered.contains("https://devblogs.microsoft.com/microsoft365dev/retirement-of-office-365-connectors-within-microsoft-teams/#why-are-we-retiring-office-365-connectors"));
        assertTrue(rendered.contains("https://docs.redhat.com/en/documentation/red_hat_hybrid_cloud_console/1-latest/html-single/integrating_the_red_hat_hybrid_cloud_console_with_third-party_applications/index#assembly-configuring-integration-with-teams_integrating-communications"));
        assertTrue(rendered.contains("Red Hat Hybrid Cloud Console."));
    }

    private static Endpoint buildEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setId(UUID.randomUUID());
        endpoint.setName("Unreliable integration");
        endpoint.setOrgId(DEFAULT_ORG_ID);
        endpoint.setType(CAMEL);
        endpoint.setSubType("slack");
        return endpoint;
    }

}
