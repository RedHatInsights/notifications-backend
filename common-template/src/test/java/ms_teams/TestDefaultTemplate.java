package ms_teams;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestDefaultTemplate {

    @Inject
    TestHelpers testHelpers;

    private static final String INVENTORY_URL = TestHelpers.expectedTestEnvUrlValue + "/insights/inventory/6ad30f3e-0497-4e74-99f1-b3f9a6120a6f";
    private static final String APPLICATION_URL = TestHelpers.expectedTestEnvUrlValue + "/insights/policies";
    private static final String TEAMS_EXPECTED_MSG = "\"text\": \"[%s](" + INVENTORY_URL + ") " +
        "triggered %d event%s from Policies - Red Hat Enterprise Linux. [Open Policies](" + APPLICATION_URL + ")\"";

    @Test
    void testRenderedDefaultTemplate() {
        final Action action = TestHelpers.createAdvisorAction("123456", "unknown");

        final String result = testHelpers.renderTemplate(
            IntegrationType.MS_TEAMS,
            "unknown",
            action,
            INVENTORY_URL,
            APPLICATION_URL
        );

        final String expectedMessage = String.format(TEAMS_EXPECTED_MSG,
            action.getContext().getAdditionalProperties().get("display_name"),
            action.getEvents().size(),
            !action.getEvents().isEmpty() ? "s" : StringUtils.EMPTY);

        // check content from parent template
        assertTrue(result.contains("\"contentType\":\"application/vnd.microsoft.card.adaptive\""));
System.out.println(result);
        // check notification body
        assertTrue(result.contains(expectedMessage));
    }
}
