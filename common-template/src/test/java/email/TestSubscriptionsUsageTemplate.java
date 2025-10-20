package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.Severity;
import helpers.SubscriptionsUsageTestHelpers;
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
        assertEquals("Instant notification - Exceeded Utilization Threshold - Subscriptions Usage - Subscription Services", result);

        // Test with Low severity level
        Action lowAction = ACTION;
        lowAction.setSeverity(Severity.LOW.name());
        String lowResult = generateEmailSubject(EXCEEDED_UTILIZATION_THRESHOLD, lowAction);
        assertEquals("[LOW] Instant notification - Exceeded Utilization Threshold - Subscriptions Usage - Subscription Services", lowResult);
    }

    @Test
    public void testUsageThresholdExceededEmailBody() {
        String result = generateEmailBody(EXCEEDED_UTILIZATION_THRESHOLD, ACTION);
        assertTrue(result.contains("Subscriptions Usage - Subscription Services"));
        assertTrue(result.contains("Usage Threshold Exceeded"));
        assertTrue(result.contains("Your <b>RHEL for x86</b> subscription usage has exceeded <b>85%</b> of capacity."));
    }
}
