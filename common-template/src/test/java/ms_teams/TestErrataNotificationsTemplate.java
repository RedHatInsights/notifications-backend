package ms_teams;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestErrataNotificationsTemplate {

    static final String BUGFIX_ERRATA = "new-subscription-bugfix-errata";
    private static final Action ACTION = ErrataTestHelpers.createErrataAction();

    private static final String ERRATA_SEARCH_URL = "https://access.redhat.com/errata-search/";
    private static final String MS_TEAMS_EXPECTED_MSG = "{\"text\":\"Red Hat published new bugfix errata that affect your products. " +
            "Explore these and others in the [errata search](" + ERRATA_SEARCH_URL + ").\"}";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateBugfixErrata() {
        String result = renderTemplate(BUGFIX_ERRATA, ACTION);
        assertEquals(MS_TEAMS_EXPECTED_MSG, result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(MS_TEAMS, "subscription-services", "errata-notifications", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
