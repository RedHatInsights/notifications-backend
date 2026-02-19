package ms_teams;


import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.Severity;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static helpers.ErrataTestHelpers.BUGFIX_ERRATA;
import static helpers.ErrataTestHelpers.BUGFIX_ERRATA_DISPLAY_NAME;
import static helpers.ErrataTestHelpers.ENHANCEMENT_ERRATA;
import static helpers.ErrataTestHelpers.ENHANCEMENT_ERRATA_DISPLAY_NAME;
import static helpers.ErrataTestHelpers.ERRATA_APPLICATION_DISPLAY_NAME;
import static helpers.ErrataTestHelpers.ERRATA_BUNDLE_DISPLAY_NAME;
import static helpers.ErrataTestHelpers.SECURITY_ERRATA;
import static helpers.ErrataTestHelpers.SECURITY_ERRATA_DISPLAY_NAME;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestErrataNotificationsTemplate {

    private static final Action RAW_ACTION = ErrataTestHelpers.createErrataAction(Severity.UNDEFINED.name());

    @Inject
    TemplateService templateService;

    @Inject
    TestHelpers testHelpers;

    private static Stream<Arguments> eventTypesAndBeta() {
        return Stream.of(
                Arguments.of(BUGFIX_ERRATA, BUGFIX_ERRATA_DISPLAY_NAME,  true),
                Arguments.of(BUGFIX_ERRATA, BUGFIX_ERRATA_DISPLAY_NAME, false),
                Arguments.of(SECURITY_ERRATA, SECURITY_ERRATA_DISPLAY_NAME, true),
                Arguments.of(SECURITY_ERRATA, SECURITY_ERRATA_DISPLAY_NAME, false),
                Arguments.of(ENHANCEMENT_ERRATA, ENHANCEMENT_ERRATA_DISPLAY_NAME, true),
                Arguments.of(ENHANCEMENT_ERRATA, ENHANCEMENT_ERRATA_DISPLAY_NAME, false)
        );
    }

    @ParameterizedTest
    @MethodSource("eventTypesAndBeta")
    void testRenderedErrataTemplates(final String eventType, final String eventTypeDisplayName, final boolean useBetaTemplate) {
        Map<String, Object> action = testHelpers.addSourceParameterToAction(RAW_ACTION, ERRATA_BUNDLE_DISPLAY_NAME, ERRATA_APPLICATION_DISPLAY_NAME, eventTypeDisplayName);
        TemplateDefinition templateConfig = new TemplateDefinition(MS_TEAMS, "subscription-services", "errata-notifications", eventType, useBetaTemplate);

        String result = templateService.renderTemplate(templateConfig, action);
        // check content from parent template
        ErrataTestHelpers.checkErrataChatTemplateContent(eventType, result, RAW_ACTION, "teams");

        if (eventType.equals(SECURITY_ERRATA)) {
            assertTrue(result.contains("\u2753 Highest severity: Undefined"));
        } else  {
            assertTrue(result.contains("\u2753 Severity: Undefined"));
        }
        assertTrue(result.contains("\"text\": \"Explore these and others in **errata search**.\""));
        assertTrue(result.contains("\"url\": \"https://access.redhat.com/errata-search/?from=notifications&integration=teams\""));
    }
}
