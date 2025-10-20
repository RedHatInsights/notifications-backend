package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.Severity;
import helpers.SubscriptionsUsageTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSubscriptionsUsageTemplate extends EmailTemplatesRendererHelper {

    static final String EXCEEDED_UTILIZATION_THRESHOLD = "exceeded-utilization-threshold";

    private static final Action ACTION = SubscriptionsUsageTestHelpers.createSubscriptionsUsageAction();

    @Override
    protected String getBundle() {
        return "subscription-services";
    }

    @Override
    protected String getApp() {
        return "subscriptions";
    }

    @Override
    protected String getBundleDisplayName() {
        return "Subscription Services";
    }

    @Override
    protected String getAppDisplayName() {
        return "Subscriptions Usage";
    }

    @Test
    public void testUsageThresholdExceededEmailTitle() {
        eventTypeDisplayName = "Usage Threshold Exceeded";
        String result = generateEmailSubject(EXCEEDED_UTILIZATION_THRESHOLD, ACTION);
        assertEquals("Instant notification - Subscription threshold exceeded - Subscriptions usage - Subscription services", result);

        // Test with Low severity level
        Action lowAction = ACTION;
        lowAction.setSeverity(Severity.LOW.name());
        String lowResult = generateEmailSubject(EXCEEDED_UTILIZATION_THRESHOLD, lowAction);
        assertEquals("[LOW] Instant notification - Subscription threshold exceeded - Subscriptions usage - Subscription services", lowResult);
    }

    @Test
    public void testUsageThresholdExceededEmailBody() {
        String result = generateEmailBody(EXCEEDED_UTILIZATION_THRESHOLD, ACTION);
        assertTrue(result.contains("Subscriptions Usage - Subscription Services"));
        assertTrue(result.contains("Subscription threshold exceeded"));
        assertTrue(result.contains("Product variant: <b>RHEL for x86</b>"));
        assertTrue(result.contains("Organization: <b>" + TestHelpers.DEFAULT_ORG_ID + "</b>"));
        // Usage information section
        assertTrue(result.contains("Usage information"));
        assertTrue(result.contains("The subscription usage for your organization has exceeded your subscription threshold."));
        // Table headers
        assertTrue(result.contains("Usage metric"));
        assertTrue(result.contains("Subscription threshold utilization"));
        // Table values
        assertTrue(result.contains(">sockets<") || result.contains("sockets</td>"));
        assertTrue(result.contains(">105%<") || result.contains("105%</td>"));
        // Find out more section with link text
        assertTrue(result.contains("Find out more"));
        assertTrue(result.contains("Getting Started with the Subscriptions Usage Service"));

    }
}
