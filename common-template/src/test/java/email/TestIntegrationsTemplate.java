package email;

import com.fasterxml.jackson.core.type.TypeReference;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Parser;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.qute.templates.mapping.Console.INTEGRATIONS_GENERAL_COMMUNICATION;
import static com.redhat.cloud.notifications.qute.templates.mapping.Console.INTEGRATIONS_INTEGRATION_DISABLED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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


    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIntegrationDisabledBodyWithClientError(boolean useBetaTemplate) {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, useBetaTemplate);
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testIntegrationDisabledBodyWithServerError(boolean useBetaTemplate) {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_5XX", "the HTTP server responded with an HTTP status", 2048, -1);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, useBetaTemplate);
        assertTrue(rendered.contains("disabled because the connection couldn't be established with the remote endpoint, or it responded too many times with a server error (HTTP status code 5xx) after 2048 attempts"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithClientErrorInFrench() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, Locale.FRENCH);
        assertTrue(rendered.contains("Intégration désactivée"));
        assertTrue(rendered.contains("a été désactivée"));
        assertTrue(rendered.contains("code de statut HTTP 401"));
        assertTrue(rendered.contains("Veuillez vérifier"));
        assertTrue(rendered.contains("Paramètres > Integrations"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledBodyWithServerErrorInFrench() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_5XX", "connexion refusée", 2048, 503);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, Locale.FRENCH);
        assertTrue(rendered.contains("Intégration désactivée"));
        assertTrue(rendered.contains("a été désactivée"));
        assertTrue(rendered.contains("tentatives"));
        assertTrue(rendered.contains("La dernière erreur était"));
        assertTrue(rendered.contains("Veuillez vérifier"));
        assertTrue(rendered.contains("Paramètres > Integrations"));
        assertTrue(rendered.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    void testIntegrationDisabledTitleInFrench() {
        eventTypeDisplayName = "Integration disabled";
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, Locale.FRENCH);
        assertTrue(rendered.contains("Intégration désactivée"));
        assertTrue(rendered.contains("Integrations - Console"));
    }

    /**
     * Tests that the general communication email works as expected.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testGeneralCommunication(boolean useBetaTemplate) {

        final String actionAsString = "{" +
            "   \"version\":\"2.0.0\"," +
            "   \"bundle\":\"console\"," +
            "   \"application\":\"integrations\"," +
            "   \"event_type\":\"general-communication\"," +
            "   \"timestamp\":\"2025-07-11T09:01:36.220643799\"," +
            "   \"org_id\":\"%s\"," +
            "   \"events\":[" +
            "      {" +
            "         \"metadata\":{}," +
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
        final String rendered = generateEmailBody(INTEGRATIONS_GENERAL_COMMUNICATION, action, useBetaTemplate);
        assertTrue(rendered.contains("ensure your allowlist is up to date"));
        assertTrue(rendered.contains("Integration one"));
        assertTrue(rendered.contains("Another integration"));
        assertTrue(rendered.contains("Third integration"));
        assertTrue(rendered.contains("before December 6th, 2026"));
        assertTrue(rendered.contains("https://access.redhat.com/articles/6985526"));
        assertTrue(rendered.contains("no further action is required"));
        assertTrue(rendered.contains("Red Hat Hybrid Cloud Console"));
        assertTrue(rendered.contains("/settings/integrations"));
        assertTrue(rendered.contains("Go to your integrations"));
        assertFalse(rendered.contains("category="));
    }

    @Test
    void testGeneralCommunicationWithFrenchLocaleRendersEnglish() {
        final String actionAsString = "{" +
            "   \"version\":\"2.0.0\"," +
            "   \"bundle\":\"console\"," +
            "   \"application\":\"integrations\"," +
            "   \"event_type\":\"general-communication\"," +
            "   \"timestamp\":\"2025-07-11T09:01:36.220643799\"," +
            "   \"org_id\":\"12345\"," +
            "   \"context\":{" +
            "      \"integration_category\":\"Reporting\"" +
            "   }," +
            "   \"events\":[" +
            "      {" +
            "         \"metadata\":{}," +
            "         \"payload\":{" +
            "            \"integration_names\":[\"My integration\"]" +
            "         }" +
            "      }" +
            "   ]," +
            "   \"recipients\":[{\"only_admins\":false,\"ignore_user_preferences\":true,\"users\":[],\"emails\":[],\"groups\":[]}]" +
            "}";
        Action action = Parser.decode(actionAsString);
        String rendered = generateEmailBody(INTEGRATIONS_GENERAL_COMMUNICATION, action, Locale.FRENCH);
        // Template has no {msg:...} calls, so locale is ignored and English is rendered as-is
        assertTrue(rendered.contains("We are reaching out to ensure your allowlist is up to date"));
        assertTrue(rendered.contains("Dear Red Hat customer"));
        assertTrue(rendered.contains("My integration"));
    }

    @Test
    void testIntegrationDisabledBodyUrlEncodesSpecialChars() {
        final String actionAsString = "{" +
            "   \"version\":\"2.0.0\"," +
            "   \"id\":\"9ee4a19b-297c-49f8-ad20-e777e6d71533\"," +
            "   \"bundle\":\"console\"," +
            "   \"application\":\"integrations\"," +
            "   \"event_type\":\"integration-disabled\"," +
            "   \"timestamp\":\"2025-07-25T08:25:09.119379869\"," +
            "   \"org_id\":\"default-org-id\"," +
            "   \"context\":{" +
            "      \"error_type\":\"HTTP_4XX\"," +
            "      \"error_details\":\"\"," +
            "      \"endpoint_id\":\"c8a30166-526a-4e6c-b0c7-d24ec35279d3\"," +
            "      \"endpoint_name\":\"My & Integration\"," +
            "      \"endpoint_category\":\"Cat <test>\"," +
            "      \"errors_count\":1," +
            "      \"status_code\":401" +
            "   }," +
            "   \"events\":[{\"payload\":{},\"metadata\":{}}]," +
            "   \"recipients\":[{\"only_admins\":true,\"ignore_user_preferences\":true,\"users\":[],\"emails\":[],\"groups\":[]}]" +
            "}";
        eventTypeDisplayName = "Integration disabled";
        Map<String, Object> action = toMapAction(Parser.decode(actionAsString));
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, false);
        // Special characters in category and name must be URL-encoded in href attributes
        assertTrue(rendered.contains("category=Cat+%3Ctest%3E"), "endpoint_category should be URL-encoded");
        assertTrue(rendered.contains("name=My+%26+Integration"), "endpoint_name should be URL-encoded");
        // HTML-encoded in link label
        assertTrue(rendered.contains("My &amp; Integration"), "endpoint_name should be HTML-escaped in link text");
    }

    @Test
    void testIntegrationDisabledBodyWithUnsupportedLocaleRendersEnglish() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, Locale.GERMAN);
        // German not supported, must fall back to English
        assertTrue(rendered.contains("disabled because the remote endpoint responded with an HTTP status code 401"));
        assertTrue(rendered.contains("Settings > Integrations"));
    }

    @Test
    void testIntegrationDisabledEscapesErrorDetails() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_5XX", "<img src=x onerror=alert(1)> & more", 3, 503);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, false);
        // error_details comes from an untrusted remote endpoint response; it must be HTML-escaped
        assertTrue(rendered.contains("&lt;img src=x onerror=alert(1)&gt; &amp; more"), "error_details should be HTML-escaped");
        assertFalse(rendered.contains("<img src=x onerror=alert(1)>"), "raw error_details HTML must not be rendered");
    }

    @Test
    void testLayoutButtonLabelInEnglish() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, false);
        assertTrue(rendered.contains("Go to Integrations"));
    }

    @Test
    void testLayoutButtonLabelInFrench() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, Locale.FRENCH);
        assertTrue(rendered.contains("Accéder à Integrations"));
    }

    @Test
    void testLayoutFooterInEnglish() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, false);
        assertTrue(rendered.contains("This email was sent by Red Hat Hybrid Cloud Console"));
        assertTrue(rendered.contains("Manage email preferences"));
    }

    @Test
    void testLayoutFooterInFrench() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, Locale.FRENCH);
        assertTrue(rendered.contains("Cet e-mail a été envoyé par Red Hat Hybrid Cloud Console"));
        assertTrue(rendered.contains("Gérer les préférences d'e-mail"));
    }

    @Test
    void testLayoutCriticalFooterInEnglish() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, true, Locale.ENGLISH);
        assertTrue(rendered.contains("it is critical or requires action"));
        assertTrue(rendered.contains("regardless of your email preferences"));
    }

    @Test
    void testLayoutCriticalFooterInFrench() {
        Map<String, Object> action = buildIntegrationDisabledActionMap("HTTP_4XX", "", 1, 401);
        String rendered = generateEmailBody(INTEGRATIONS_INTEGRATION_DISABLED, action, true, Locale.FRENCH);
        assertTrue(rendered.contains("il est critique ou nécessite une action"));
        assertTrue(rendered.contains("vos préférences en matière d"));
    }

    private Map<String, Object> buildIntegrationDisabledActionMap(final String errorType, final String errorDetails, final int errorCount, final int errorStatusCode) {
        return toMapAction(buildIntegrationDisabledAction(errorType, errorDetails, errorCount, errorStatusCode));
    }

    /**
     * Converts an {@link Action} into the Map-shaped action used in production. {@code EmailMessageHandler}
     * passes the raw {@code eventData} map (not an {@code ingress.Action}), and the engine
     * ({@code EmailProcessor.convertEventAsDataMap}) adds a camelCase {@code orgId} key on top of the
     * snake_case wire format, which the shared layout references as {@code {action.orgId}}.
     */
    private Map<String, Object> toMapAction(final Action action) {
        try {
            Map<String, Object> mapAction = objectMapper.readValue(Parser.encode(action), new TypeReference<Map<String, Object>>() { });
            mapAction.put("orgId", mapAction.get("org_id"));
            return mapAction;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
