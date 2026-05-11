package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.Severity;
import helpers.SubscriptionsUsageTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSubscriptionsUsageTemplate extends EmailTemplatesRendererHelper {

    static final String EXCEEDED_UTILIZATION_THRESHOLD = "exceeded-utilization-threshold";
    static final String EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD = "exceeded-custom-utilization-threshold";

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

    @AfterEach
    void cleanup() {
        ACTION.setSeverity(null);
        eventTypeDisplayName = null;
    }

    @Test
    public void testUsageThresholdExceededEmailTitle() {
        eventTypeDisplayName = "Subscription threshold exceeded";
        String result = generateEmailSubject(EXCEEDED_UTILIZATION_THRESHOLD, ACTION);
        assertEquals("Instant notification - Subscription threshold exceeded - Subscriptions Usage - Subscription Services", result);

        // Test with Low severity level
        ACTION.setSeverity(Severity.LOW.name());
        String lowResult = generateEmailSubject(EXCEEDED_UTILIZATION_THRESHOLD, ACTION);
        assertEquals("[LOW] Instant notification - Subscription threshold exceeded - Subscriptions Usage - Subscription Services", lowResult);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUsageThresholdExceededEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(EXCEEDED_UTILIZATION_THRESHOLD, ACTION, useBetaTemplate);
        assertTrue(result.contains("Subscriptions Usage - Subscription Services"));
        assertTrue(result.contains("Subscription threshold exceeded"));
        assertTrue(result.contains("Product variant: <b>RHEL for x86</b>"));
        assertTrue(result.contains("SLA: <b>Premium</b>"));
        assertTrue(result.contains("Usage: <b>Production</b>"));
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
        assertTrue(result.contains("Getting Started with the Subscriptions Usage Service"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUsageThresholdExceededEmailBodyWithSlaOnly(boolean useBetaTemplate) {
        Action actionWithSlaOnly = SubscriptionsUsageTestHelpers.createSubscriptionsUsageActionWithSlaOnly();
        String result = generateEmailBody(EXCEEDED_UTILIZATION_THRESHOLD, actionWithSlaOnly, useBetaTemplate);
        assertTrue(result.contains("SLA: <b>Premium</b>"));
        assertFalse(result.contains("Usage:"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUsageThresholdExceededEmailBodyWithUsageOnly(boolean useBetaTemplate) {
        Action actionWithUsageOnly = SubscriptionsUsageTestHelpers.createSubscriptionsUsageActionWithUsageOnly();
        String result = generateEmailBody(EXCEEDED_UTILIZATION_THRESHOLD, actionWithUsageOnly, useBetaTemplate);
        assertFalse(result.contains("SLA:"));
        assertTrue(result.contains("Usage: <b>Production</b>"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testUsageThresholdExceededEmailBodyWithoutSlaAndUsage(boolean useBetaTemplate) {
        Action actionWithoutSlaAndUsage = SubscriptionsUsageTestHelpers.createSubscriptionsUsageActionWithoutSlaAndUsage();
        String result = generateEmailBody(EXCEEDED_UTILIZATION_THRESHOLD, actionWithoutSlaAndUsage, useBetaTemplate);
        assertFalse(result.contains("SLA:"));
        assertFalse(result.contains("Usage:"));
    }

    @Test
    public void testExceededCustomUtilizationThresholdEmailTitle() {
        eventTypeDisplayName = "Usage at custom percentage limit reached";
        String result = generateEmailSubject(EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD, ACTION);
        assertEquals("Instant notification - Usage at custom percentage limit reached - Subscriptions Usage - Subscription Services", result);

        // Test with Low severity level
        ACTION.setSeverity(Severity.LOW.name());
        String lowResult = generateEmailSubject(EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD, ACTION);
        assertEquals("[LOW] Instant notification - Usage at custom percentage limit reached - Subscriptions Usage - Subscription Services", lowResult);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testExceededCustomUtilizationThresholdEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD, ACTION, useBetaTemplate);
        assertTrue(result.contains("Subscriptions Usage - Subscription Services"));
        assertTrue(result.contains("Proactive subscription usage notification: Usage at custom percentage limit reached"));
        assertTrue(result.contains("Product variant: <b>RHEL for x86</b>"));
        assertTrue(result.contains("SLA: <b>Premium</b>"));
        assertTrue(result.contains("Usage: <b>Production</b>"));
        assertTrue(result.contains("Organization: <b>" + TestHelpers.DEFAULT_ORG_ID + "</b>"));
        // Proactive usage information section
        assertTrue(result.contains("Proactive usage information"));
        assertTrue(result.contains("Your subscription usage for this organization has exceeded your custom threshold."));
        // Table headers
        assertTrue(result.contains("Usage metric"));
        assertTrue(result.contains("Subscription threshold utilization"));
        // Table values
        assertTrue(result.contains(">sockets<") || result.contains("sockets</td>"));
        assertTrue(result.contains(">105%<") || result.contains("105%</td>"));
        assertTrue(result.contains("Getting Started with the Subscriptions Usage Service"));
        // Verify URL contains encoded product_id in path
        assertTrue(result.contains("/subscriptions/usage/RHEL%20for%20x86?"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testExceededCustomUtilizationThresholdEmailBodyWithSlaOnly(boolean useBetaTemplate) {
        Action actionWithSlaOnly = SubscriptionsUsageTestHelpers.createSubscriptionsUsageActionWithSlaOnly();
        String result = generateEmailBody(EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD, actionWithSlaOnly, useBetaTemplate);
        assertTrue(result.contains("SLA: <b>Premium</b>"));
        assertFalse(result.contains("Usage:"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testExceededCustomUtilizationThresholdEmailBodyWithUsageOnly(boolean useBetaTemplate) {
        Action actionWithUsageOnly = SubscriptionsUsageTestHelpers.createSubscriptionsUsageActionWithUsageOnly();
        String result = generateEmailBody(EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD, actionWithUsageOnly, useBetaTemplate);
        assertFalse(result.contains("SLA:"));
        assertTrue(result.contains("Usage: <b>Production</b>"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testExceededCustomUtilizationThresholdEmailBodyWithoutSlaAndUsage(boolean useBetaTemplate) {
        Action actionWithoutSlaAndUsage = SubscriptionsUsageTestHelpers.createSubscriptionsUsageActionWithoutSlaAndUsage();
        String result = generateEmailBody(EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD, actionWithoutSlaAndUsage, useBetaTemplate);
        assertFalse(result.contains("SLA:"));
        assertFalse(result.contains("Usage:"));
    }
}
