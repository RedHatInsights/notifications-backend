package google_chat;

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

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.GOOGLE_CHAT;
import static helpers.ErrataTestHelpers.BUGFIX_ERRATA;
import static helpers.ErrataTestHelpers.ENHANCEMENT_ERRATA;
import static helpers.ErrataTestHelpers.SECURITY_ERRATA;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestErrataNotificationsTemplate {

    private static final Action ACTION = ErrataTestHelpers.createErrataAction(Severity.MODERATE.name());

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
        TemplateDefinition templateConfig = new TemplateDefinition(GOOGLE_CHAT, "subscription-services", "errata-notifications", eventType, useBetaTemplate);
        String result = templateService.renderTemplate(templateConfig, ACTION);
        ErrataTestHelpers.checkErrataChatTemplateContent(eventType, result, ACTION, "google_chat");

        if (useBetaTemplate) {
            if (eventType.equals(SECURITY_ERRATA)) {
                assertTrue(result.contains("Highest severity: Moderate"));
            } else {
                assertTrue(result.contains("Severity: Moderate"));
            }
            assertTrue(result.contains("\"text\": \"Explore these and others in the <b>errata search</b>.\""));
            assertTrue(result.contains("\"url\": \"https://access.redhat.com/errata-search/?from=notifications&integration=google_chat\""));
        }
    }
}
