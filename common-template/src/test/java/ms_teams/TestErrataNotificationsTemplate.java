package ms_teams;


import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
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
        TemplateDefinition templateConfig = new TemplateDefinition(MS_TEAMS, "subscription-services", "errata-notifications", eventType);
        String result = templateService.renderTemplate(templateConfig, ACTION);
        // check content from parent template
        AdaptiveCardValidatorHelper.validate(result);
        ErrataTestHelpers.checkErrataChatTemplateContent(eventType, result, ACTION, "teams");
    }
}
