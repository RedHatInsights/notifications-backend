package google_chat;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.GOOGLE_CHAT;
import static helpers.ErrataTestHelpers.BUGFIX_ERRATA;
import static helpers.ErrataTestHelpers.ENHANCEMENT_ERRATA;
import static helpers.ErrataTestHelpers.SECURITY_ERRATA;

@QuarkusTest
class TestErrataNotificationsTemplate {

    private static final Action ACTION = ErrataTestHelpers.createErrataAction();

    @Inject
    TemplateService templateService;

    @ValueSource(strings = { BUGFIX_ERRATA, SECURITY_ERRATA, ENHANCEMENT_ERRATA })
    @ParameterizedTest
    void testRenderedErrataTemplates(final String eventType) {
        TemplateDefinition templateConfig = new TemplateDefinition(GOOGLE_CHAT, "subscription-services", "errata-notifications", eventType);
        String result = templateService.renderTemplate(templateConfig, ACTION);
        ErrataTestHelpers.checkErrataChatTemplateContent(eventType, result, ACTION, "google_chat");
    }
}
