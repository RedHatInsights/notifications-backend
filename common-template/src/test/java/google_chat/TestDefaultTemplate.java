package google_chat;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.Severity;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestDefaultTemplate {

    @Inject
    TestHelpers testHelpers;

    private static final String INVENTORY_URL = TestHelpers.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f";
    private static final String APPLICATION_URL = TestHelpers.expectedTestEnvUrlValue + "/insights/policies";
    private static final String APP_BUNDLE_HEADER = "Policies - Red Hat Enterprise Linux";

    private static final String GOOGLE_CHAT_EXPECTED_MAIN_MSG = "<a href=\\\"" + INVENTORY_URL + "\\\">%s</a> " + "triggered %d event%s";
    private static final String GOOGLE_CHAT_EXPECTED_EXPLORE_APP_MSG = "Explore %s and others in <b>Policies</b>.";

    private static Stream<Arguments> betaAndSeverity() {
        return Stream.of(
                Arguments.of(false, null), // old template without severity
                Arguments.of(true, null), // beta template without severity
                Arguments.of(true, Severity.NONE.name()), // beta template with none severity
                Arguments.of(true, Severity.CRITICAL.name()) // beta template with critical severity
        );
    }

    @ParameterizedTest
    @MethodSource("betaAndSeverity")
    void testRenderedDefaultTemplate(boolean useBetaTemplates, String severity) {
        final Action action = TestHelpers.createAdvisorAction("123456", "unknown", severity);

        final String result = testHelpers.renderTemplate(
            IntegrationType.GOOGLE_CHAT,
            "unknown",
            action,
            INVENTORY_URL,
            APPLICATION_URL,
            useBetaTemplates
        );

        assertTrue(result.contains(TestHelpers.NOT_USE_EVENT_TYPE + " - " + APP_BUNDLE_HEADER));
        switch (severity) {
            case null -> assertFalse(result.contains("\"iconUrl\": \"https://console.redhat.com/apps/frontend-assets/email-assets/severities/"));
            case "NONE" -> {
                assertTrue(result.contains("\"iconUrl\": \"https://console.redhat.com/apps/frontend-assets/email-assets/severities/none.png\""));
                assertTrue(result.contains("Severity: None"));
                assertFalse(result.contains("</font>"));
            }
            case "CRITICAL" -> {
                assertTrue(result.contains("\"iconUrl\": \"https://console.redhat.com/apps/frontend-assets/email-assets/severities/critical.png\""));
                assertTrue(result.contains("Severity: <font color=\\\"#B1380B\\\"><b>Critical</b></font>"));
            }
            default -> {
                // Nothing
            }
        }

        assertTrue(result.contains(
                String.format(GOOGLE_CHAT_EXPECTED_MAIN_MSG,
                        action.getContext().getAdditionalProperties().get("display_name"),
                        action.getEvents().size(),
                        action.getEvents().size() != 1 ? "s" : StringUtils.EMPTY
                )));
        assertTrue(result.contains(String.format(GOOGLE_CHAT_EXPECTED_EXPLORE_APP_MSG,
                action.getEvents().size() != 1 ? "these" : "this")));

        // Open Policies button
        assertTrue(result.contains("\"text\": \"Open Policies\","));
        assertTrue(result.contains("\"onClick\": {"));
        assertTrue(result.contains("\"url\": \"" + APPLICATION_URL + "\""));
    }
}
