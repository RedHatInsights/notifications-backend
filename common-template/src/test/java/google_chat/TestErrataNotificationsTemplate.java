package google_chat;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.ErrataTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.GOOGLE_CHAT;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestErrataNotificationsTemplate {

    static final String BUGFIX_ERRATA = "new-subscription-bugfix-errata";
    private static final Action ACTION = ErrataTestHelpers.createErrataAction();

    private static final String ERRATA_SEARCH_URL = "https://access.redhat.com/errata-search/";
    private static final String GOOGLE_CHAT_EXPECTED_MSG = "{\"text\":\"Red Hat published new bugfix errata that affect your products. " +
            "Explore these and others in the <" + ERRATA_SEARCH_URL + "|errata search>.\"}";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateBugfixErrata() {
        String result = renderTemplate(BUGFIX_ERRATA, ACTION);
        assertEquals(GOOGLE_CHAT_EXPECTED_MSG, result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(GOOGLE_CHAT, "subscription-services", "errata-notifications", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
