package ms_teams;


import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.Severity;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static helpers.ErrataTestHelpers.BUGFIX_ERRATA;
import static helpers.ErrataTestHelpers.ENHANCEMENT_ERRATA;
import static helpers.ErrataTestHelpers.SECURITY_ERRATA;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestErrataNotificationsTemplate {

    private static final Action ACTION = ErrataTestHelpers.createErrataAction(Severity.UNDEFINED.name());

    @Inject
    TemplateService templateService;

    private static Stream<Arguments> eventTypesAndBeta() {
        return Stream.of(
                Arguments.of(BUGFIX_ERRATA, true),
                Arguments.of(BUGFIX_ERRATA, false),
                Arguments.of(SECURITY_ERRATA, true),
                Arguments.of(SECURITY_ERRATA, false),
                Arguments.of(ENHANCEMENT_ERRATA, true),
                Arguments.of(ENHANCEMENT_ERRATA, false)
        );
    }

    @ParameterizedTest
    @MethodSource("eventTypesAndBeta")
    void testRenderedErrataTemplates(final String eventType, final boolean useBetaTemplate) {
        TemplateDefinition templateConfig = new TemplateDefinition(MS_TEAMS, "subscription-services", "errata-notifications", eventType, useBetaTemplate);
        String result = templateService.renderTemplate(templateConfig, ACTION);
        // check content from parent template
        ErrataTestHelpers.checkErrataChatTemplateContent(eventType, result, ACTION, "teams");

        if (useBetaTemplate) {
            if (eventType.equals(SECURITY_ERRATA)) {
                assertTrue(result.contains("\u2753 Highest severity: Undefined"));
            } else  {
                assertTrue(result.contains("\u2753 Severity: Undefined"));
            }
        } else {
            AdaptiveCardValidatorHelper.validate(result);
        }
    }
}
