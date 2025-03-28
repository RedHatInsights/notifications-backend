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
class TestAdvisorOpenShiftTemplate {

    static final String NEW_RECOMMENDATION = "new-recommendation";

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateForNewRecommendations() {
        Action action = TestHelpers.createAdvisorAction("123456", NEW_RECOMMENDATION);
        String result = templateService.renderTemplate(new TemplateDefinition(IntegrationType.DRAWER, "openshift", "advisor", NEW_RECOMMENDATION), action);
        assertEquals("**My Host** has 4 new recommendations.", result);
    }
}
