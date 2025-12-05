package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.ingress.Action;
import helpers.PatchTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPatchTemplate extends EmailTemplatesRendererHelper {

    static final String NEW_ADVISORY = "new-advisory";

    private static final Action ACTION = PatchTestHelpers.createPatchAction();

    public static final String JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT = "{" +
        "   \"patch\":{" +
        "      \"security\":[" +
        "         {" +
        "            \"name\":\"advisory_1\"," +
        "            \"synopsis\":\"synopsis\"" +
        "         }" +
        "      ]," +
        "      \"bugfix\":[" +
        "         {" +
        "            \"name\":\"advisory_4\"," +
        "            \"synopsis\":\"synopsis\"" +
        "         }" +
        "      ]," +
        "      \"enhancement\":[" +
        "         {" +
        "            \"name\":\"advisory_2\"," +
        "            \"synopsis\":\"synopsis\"" +
        "         }," +
        "         {" +
        "            \"name\":\"advisory_3\"," +
        "            \"synopsis\":\"synopsis\"" +
        "         }" +
        "      ]," +
        "      \"other\":[" +
        "         " +
        "      ]" +
        "   }," +
        "   \"total_advisories\":4," +
        "   \"start_time\":null," +
        "   \"end_time\":null" +
        "}";

    @Override
    protected String getApp() {
        return "patch";
    }

    @Override
    protected String getAppDisplayName() {
        return "Patch";
    }

    @Test
    public void testNewAdvisoryEmailTitle() {
        eventTypeDisplayName = "New advisory";
        String result = generateEmailSubject(NEW_ADVISORY, ACTION);
        assertEquals("Instant notification - New advisory - Patch - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testNewAdvisoryEmailBody() {
        String result = generateEmailBody(NEW_ADVISORY, ACTION);
        assertTrue(result.contains("Red Hat Insights has just released new Advisories for your organization"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyDigestEmailBody() throws JsonProcessingException {
        String result = generateAggregatedEmailBody(JSON_PATCH_DEFAULT_AGGREGATION_CONTEXT);
        assertTrue(result.contains("There are 4 new advisories affecting your systems."));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
