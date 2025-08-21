package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.qute.templates.mapping.Console.INTEGRATIONS_GENERAL_COMMUNICATION;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.INTEGRATIONS_INTEGRATION_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestIntegrationsTemplate extends EmailTemplatesRendererHelper {

    @Override
    protected String getBundle() {
        return "console";
    }

    @Override
    protected String getApp() {
        return "integrations";
    }

    @Override
    protected String getBundleDisplayName() {
        return "Console";
    }

    @Override
    protected String getAppDisplayName() {
        return "Integrations";
    }

    @Test
    void testIntegrationDisabledTitle() {
        Action action = buildIntegrationDisabledAction("HTTP_4XX", "", 1, 401);
        String result = generateEmailSubject(INTEGRATIONS_INTEGRATION_DISABLED, action);
        assertEquals("Instant notification - Integration disabled - Integrations - Console", result);
    }


    @Test
    void testIntegrationDisabledBodyWithClientError() {
        Action action = buildIntegrationDisabledAction("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action);
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithServerError() {
        Action action = buildIntegrationDisabledAction("HTTP_5XX", "the HTTP server responded with an HTTP status", 2048, -1);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action);
        assertTrue(rendered.contains("disabled because the connection couldn't be established with the remote endpoint, or it responded too many times with a server error (HTTP status code 5xx) after 2048 attempts"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    /**
     * Tests that the general communication email works as expected.
     */
    @Test
    void testGeneralCommunication() {

        final String actionAsString = "{" +
            "   \"version\":\"2.0.0\"," +
            "   \"bundle\":\"console\"," +
            "   \"application\":\"integrations\"," +
            "   \"event_type\":\"general-communication\"," +
            "   \"timestamp\":\"2025-07-11T09:01:36.220643799\"," +
            "   \"org_id\":\"%s\"," +
            "   \"context\":{" +
            "      \"integration_category\":\"Communications\"" +
            "   }," +
            "   \"events\":[" +
            "      {" +
            "         \"metadata\":{" +
            "            \"communication-description\":\"General communication about customers' Microsoft Teams' URLs needing a review\"" +
            "         }," +
            "         \"payload\":{" +
            "            \"integration_names\":[%s]" +
            "         }" +
            "      }" +
            "   ]," +
            "   \"recipients\":[" +
            "      {" +
            "         \"only_admins\":false," +
            "         \"ignore_user_preferences\":true," +
            "         \"users\":[]," +
            "         \"emails\":[]," +
            "         \"groups\":[]" +
            "      }" +
            "   ]" +
            "}";

        final String orgId = "12345";
        final List<String> integrationNames = List.of(
            "Integration one",
            "Another integration",
            "Third integration"
        );

        Action action = Parser.decode(
            String.format(actionAsString,
                orgId,
                integrationNames.stream().map(integrationName -> "\"" +  integrationName + "\"").collect(Collectors.joining(",")))
        );
        final String rendered = generateEmailBody(INTEGRATIONS_GENERAL_COMMUNICATION, action);
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
        assertTrue(rendered.contains("Red Hat Hybrid Cloud Console."));
    }

    private Action buildIntegrationDisabledAction(final String errorType, final String errorDetails, final int errorCount, final int errorStatusCode) {
        final String actionAsString = "{" +
            "   \"version\":\"2.0.0\"," +
            "   \"id\":\"9ee4a19b-297c-49f8-ad20-e777e6d71533\"," +
            "   \"bundle\":\"console\"," +
            "   \"application\":\"integrations\"," +
            "   \"event_type\":\"integration-disabled\"," +
            "   \"timestamp\":\"2025-07-25T08:25:09.119379869\"," +
            "   \"org_id\":\"default-org-id\"," +
            "   \"context\":{" +
            "      \"error_type\":\"%s\"," +
            "      \"error_details\":\"%s\"," +
            "      \"endpoint_id\":\"c8a30166-526a-4e6c-b0c7-d24ec35279d3\"," +
            "      \"endpoint_name\":\"Unreliable integration\"," +
            "      \"endpoint_category\":\"Communications\"," +
            "      \"errors_count\":%d," +
            "      \"status_code\":%d" +
            "   }," +
            "   \"events\":[" +
            "      {" +
            "         \"payload\":{" +
            "            " +
            "         }," +
            "         \"metadata\":{" +
            "            " +
            "         }" +
            "      }" +
            "   ]," +
            "   \"recipients\":[" +
            "      {" +
            "         \"only_admins\":true," +
            "         \"ignore_user_preferences\":true," +
            "         \"users\":[" +
            "            " +
            "         ]," +
            "         \"emails\":[" +
            "            " +
            "         ]," +
            "         \"groups\":[" +
            "            " +
            "         ]" +
            "      }" +
            "   ]" +
            "}";

        eventTypeDisplayName = "Integration disabled";
        return Parser.decode(
            String.format(actionAsString,
                errorType,
                errorDetails,
                errorCount,
                errorStatusCode)
        );
    }
}
