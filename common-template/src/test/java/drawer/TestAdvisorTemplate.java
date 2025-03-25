package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestAdvisorTemplate {

    public static final String NEW_RECOMMENDATION = "new-recommendation";
    public static final String RESOLVED_RECOMMENDATION = "resolved-recommendation";
    public static final String DEACTIVATED_RECOMMENDATION = "deactivated-recommendation";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateForResolvedRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", RESOLVED_RECOMMENDATION);
        String result = renderTemplate(RESOLVED_RECOMMENDATION, action);
        assertEquals("**My Host** has 4 resolved recommendations.", result);
    }

    @Test
    void testRenderedTemplateForNewRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
        String result = renderTemplate(NEW_RECOMMENDATION, action);
        assertEquals("**My Host** has 4 new recommendations.", result);
    }

    @Test
    void testRenderedTemplateForDeactivatedRecommendation() {
        Action action = TestHelpers.createAdvisorAction("123456", DEACTIVATED_RECOMMENDATION);
        String result = renderTemplate(DEACTIVATED_RECOMMENDATION, action);
        assertEquals("2 recommendations have recently been deactivated by Red Hat Insights and are no longer affecting your systems.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "advisor", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
