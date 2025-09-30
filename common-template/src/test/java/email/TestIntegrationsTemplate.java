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
        assertEquals("[IMPORTANT] Instant notification - Integration disabled - Integrations - Console", result);
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
        assertTrue(rendered.contains("We are contacting you because you have the following Microsoft Teams integrations set up for your organization in the"));
        assertTrue(rendered.contains("Integration one"));
        assertTrue(rendered.contains("Another integration"));
        assertTrue(rendered.contains("Third integration"));
        assertTrue(rendered.contains("retiring Office 365 connectors in favor of Power Automate workflows"));
        assertTrue(rendered.contains("https://devblogs.microsoft.com/microsoft365dev/retirement-of-office-365-connectors-within-microsoft-teams/#why-are-we-retiring-office-365-connectors"));
        assertTrue(rendered.contains("https://docs.redhat.com/en/documentation/red_hat_hybrid_cloud_console/1-latest/html-single/integrating_the_red_hat_hybrid_cloud_console_with_third-party_applications/index#assembly-configuring-integration-with-teams_integrating-communications"));
        assertTrue(rendered.contains("Red Hat Hybrid Cloud Console"));
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
            "   \"severity\":\"IMPORTANT\"," +
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
