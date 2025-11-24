package slack;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestDefaultTemplate {

    @Inject
    TestHelpers testHelpers;

    private static final String INVENTORY_URL = TestHelpers.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f?from=notifications&integration=slack";
    private static final String APPLICATION_URL = TestHelpers.expectedTestEnvUrlValue + "/insights/policies?from=notifications&integration=slack";
    private static final String APP_BUNDLE_HEADER = "Policies - Red Hat Enterprise Linux";

    private static final String SLACK_EXPECTED_MAIN_MSG = "<" + INVENTORY_URL + "|%s> " + "triggered %d event%s";
    private static final String SLACK_EXPECTED_OPEN_APP_MSG = "<" + APPLICATION_URL + "|Open Policies>";
    private static final String SLACK_EXPECTED_EXPLORE_APP_MSG = "Explore %s and others in *<" + APPLICATION_URL + "|Policies>*.";

    private static Stream<Arguments> betaAndSeverity() {
        return Stream.of(
                Arguments.of(false, null), // old template without severity
                Arguments.of(true, null), // beta template without severity
                Arguments.of(true, Severity.IMPORTANT.name()) // beta template with severity
        );
    }

    @ParameterizedTest
    @MethodSource("betaAndSeverity")
    void testRenderedDefaultTemplate(boolean useBetaTemplate, String severity) {
        final Action action = TestHelpers.createAdvisorAction("123456", "unknown", severity);

        final String result = testHelpers.renderTemplate(
            IntegrationType.SLACK,
            "unknown",
            action,
            INVENTORY_URL,
            APPLICATION_URL,
            useBetaTemplate
        );

        if (useBetaTemplate) {
            assertTrue(result.contains(APP_BUNDLE_HEADER));
            if (severity == null) {
                assertFalse(result.contains("\"type\": \"context\","));
            } else {
                assertTrue(result.contains("\uD83D\uDFE0 Severity: Important"));
            }

            assertTrue(result.contains(
                    String.format(SLACK_EXPECTED_MAIN_MSG,
                            action.getContext().getAdditionalProperties().get("display_name"),
                            action.getEvents().size(),
                            action.getEvents().size() != 1 ? "s" : StringUtils.EMPTY)));
            assertTrue(result.contains(String.format(SLACK_EXPECTED_EXPLORE_APP_MSG,
                    action.getEvents().size() != 1 ? "these" : "this")));
        } else {
            final String expectedMessage = String.format(SLACK_EXPECTED_MAIN_MSG + " from " + APP_BUNDLE_HEADER + ". " + SLACK_EXPECTED_OPEN_APP_MSG,
                    action.getContext().getAdditionalProperties().get("display_name"),
                    action.getEvents().size(),
                    action.getEvents().size() != 1 ? "s" : StringUtils.EMPTY);

            assertEquals(expectedMessage, result);
        }
    }
}
