package slack;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.SLACK;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestErrataNotificationsTemplate {

    static final String BUGFIX_ERRATA = "new-subscription-bugfix-errata";
    private static final Action ACTION = ErrataTestHelpers.createErrataAction();

    private static final String ERRATA_SEARCH_URL = "https://access.redhat.com/errata-search/";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateBugfixErrata() {
        String result = renderTemplate(BUGFIX_ERRATA, ACTION);
        assertEquals("Red Hat published new bugfix errata that affect your products. Explore these and others in the <" + ERRATA_SEARCH_URL + "|errata search>.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(SLACK, "subscription-services", "errata-notifications", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
