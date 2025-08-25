package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.ingress.Action;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestImageBuilderTemplate extends EmailTemplatesRendererHelper {

    static final String LAUNCH_SUCCESS = "launch-success";
    static final String LAUNCH_FAILURE = "launch-failed";

    private static final Action SUCCESS_ACTION = TestHelpers.createImageBuilderAction(LAUNCH_SUCCESS);
    private static final Action FAILURE_ACTION = TestHelpers.createImageBuilderAction(LAUNCH_FAILURE);

    @Override
    protected String getApp() {
        return "image-builder";
    }

    @Override
    protected String getAppDisplayName() {
        return "Images";
    }

    public static final String JSON_IMAGE_BUILDER_DEFAULT_AGGREGATION_CONTEXT = "{" +
        "   \"images\":{" +
        "      \"aws\":{" +
        "         \"instances\":4," +
        "         \"launch_success\":2," +
        "         \"failure\":1" +
        "      }," +
        "      \"gcp\":{" +
        "         \"instances\":0," +
        "         \"launch_success\":0," +
        "         \"failure\":0" +
        "      }," +
        "      \"azure\":{" +
        "         \"instances\":0," +
        "         \"launch_success\":0," +
        "         \"failure\":0" +
        "      }," +
        "      \"errors\":[" +
        "         null" +
        "      ]," +
        "      \"launch_success\":2" +
        "   }," +
        "   \"start_time\":null," +
        "   \"end_time\":null" +
        "}";

    @Test
    public void testSuccessLaunchEmailTitle() {
        eventTypeDisplayName = "Launch completed";
        String result = generateEmailSubject(LAUNCH_SUCCESS, SUCCESS_ACTION);
        assertEquals("Instant notification - Launch completed - Images - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testSuccessLaunchEmailBody() {
        String result = generateEmailBody(LAUNCH_SUCCESS, SUCCESS_ACTION);
        assertTrue(result.contains("Instances launched successfully"));
        assertTrue(result.contains("91.123.32.4"));
        // testing empty dns value
        assertTrue(result.contains("n/a"));
    }

    @Test
    public void testFailedLaunchEmailTitle() {
        eventTypeDisplayName = "Launch failed";
        String result = generateEmailSubject(LAUNCH_FAILURE, FAILURE_ACTION);
        assertEquals("Instant notification - Launch failed - Images - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testFailedLaunchEmailBody() {
        String result = generateEmailBody(LAUNCH_FAILURE, FAILURE_ACTION);
        assertTrue(result.contains("An image failed to launch"));
        assertTrue(result.contains("Some launch error"));
    }

    @Test
    public void testDailyTemplate() throws JsonProcessingException {
        String resultBody = generateAggregatedEmailBody(JSON_IMAGE_BUILDER_DEFAULT_AGGREGATION_CONTEXT);
        assertTrue(resultBody.contains("2 launch attempts deployed"));
        assertTrue(resultBody.contains("1 launch attempts failed"));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
