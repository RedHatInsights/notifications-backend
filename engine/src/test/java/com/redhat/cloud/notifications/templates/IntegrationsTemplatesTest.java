package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
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
        return List.of(INTEGRATION_DISABLED_EVENT_TYPE, GeneralCommunicationsHelper.GENERAL_COMMUNICATIONS_EVENT_TYPE);
    }

    @Test
    void testIntegrationDisabledTitle() {
        Endpoint endpoint = buildEndpoint();
        Action action = buildIntegrationDisabledAction(endpoint, HTTP_4XX, 401, 1);
        String result = generateEmailSubject(INTEGRATION_DISABLED_EVENT_TYPE, action);
        assertEquals("Instant notification - Integrations - Console", result);
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
        assertTrue(rendered.contains("Dear Red Hat customer,"));
        assertTrue(rendered.contains("We are contacting you because you have the following Microsoft Teams integrations set up for your organization in the"));
        assertTrue(rendered.contains("Red Hat Hybrid Cloud Console"));
        assertTrue(rendered.contains("Integration one"));
        assertTrue(rendered.contains("Another integration"));
        assertTrue(rendered.contains("Third integration"));
        assertTrue(rendered.contains("Microsoft is"));
        assertTrue(rendered.contains("updating the Teams connectors for security reasons"));
        assertTrue(rendered.contains(", and they are recommending that their customers"));
        assertTrue(rendered.contains("update any URLs for connectors"));
        assertTrue(rendered.contains("they have set up. We identified the above Integrations in your account and are asking you to review, and update if needed, to avoid service interruption when Microsoft sunsets the old URLs."));
        assertTrue(rendered.contains("If you have already updated your Integrations URLs, you can safely ignore this email"));
        assertTrue(rendered.contains("To update your Microsoft Teams integrations, complete the following steps:"));
        assertTrue(rendered.contains("Generate a new webhook URL from your Microsoft Teams client by following the steps in"));
        assertTrue(rendered.contains("Update connectors URL"));
        assertTrue(rendered.contains("in the Microsoft documentation"));
        assertTrue(rendered.contains("Copy the URL to your clipboard to use in the Hybrid Cloud Console."));
        assertTrue(rendered.contains("To update your integration, log in to the"));
        assertTrue(rendered.contains("Hybrid Cloud Console"));
        assertTrue(rendered.contains("Navigate to"));
        assertTrue(rendered.contains("Settings"));
        assertTrue(rendered.contains("(gear icon) >"));
        assertTrue(rendered.contains("Integrations"));
        assertTrue(rendered.contains("Click the"));
        assertTrue(rendered.contains("Communications"));
        assertTrue(rendered.contains("tab"));
        assertTrue(rendered.contains("Locate your Microsoft Teams integration."));
        assertTrue(rendered.contains("Next to your integration, click the options icon (⋮) and click"));
        assertTrue(rendered.contains("Edit"));
        assertTrue(rendered.contains("Click"));
        assertTrue(rendered.contains("Next"));
        assertTrue(rendered.contains("to continue past the Integration type step."));
        assertTrue(rendered.contains("Paste the incoming webhook URL that you copied from Microsoft Teams into the"));
        assertTrue(rendered.contains("Endpoint URL"));
        assertTrue(rendered.contains("field."));
        assertTrue(rendered.contains("Next"));
        assertTrue(rendered.contains("Review the integration details and click"));
        assertTrue(rendered.contains("Submit"));
        assertTrue(rendered.contains("to update the integration."));
        assertTrue(rendered.contains("Following the above steps will ensure that you will continue receiving the alerts and notifications in the Microsoft Teams channel that you have configured."));
        assertTrue(rendered.contains("For more information about configuring Microsoft Teams integrations, see the"));
        assertTrue(rendered.contains("Red Hat documentation"));
        assertTrue(rendered.contains("Thank you for your attention."));
        assertTrue(rendered.contains("Kind regards,"));
        assertTrue(rendered.contains("Red Hat"));
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
