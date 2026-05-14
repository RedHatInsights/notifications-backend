package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.SubscriptionsUsageTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestSubscriptionsUsageTemplate {

    static final String EXCEEDED_UTILIZATION_THRESHOLD = "exceeded-utilization-threshold";
    static final String EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD = "exceeded-custom-utilization-threshold";

    private static final Action ACTION = SubscriptionsUsageTestHelpers.createSubscriptionsUsageAction();

    @Inject
    TestHelpers testHelpers;

    private static final String PRODUCT_LINK = "**[RHEL for x86](https://localhost/subscriptions/usage/RHEL%20for%20x86?from=notifications&integration=drawer)**";

    @Test
    void testRenderedTemplateUsageThresholdExceeded() {
        String result = renderTemplate(EXCEEDED_UTILIZATION_THRESHOLD, ACTION);
        assertEquals("The subscription usage for product variant " + PRODUCT_LINK + " has exceeded the subscription threshold with a utilization of **105%** for the **sockets** metric.", result);
    }

    @Test
    void testRenderedTemplateCustomUtilizationThresholdExceeded() {
        String result = renderTemplate(EXCEEDED_CUSTOM_UTILIZATION_THRESHOLD, ACTION);
        assertEquals("The subscription usage for product variant " + PRODUCT_LINK + " has exceeded your custom threshold with a utilization of **105%** for the **sockets** metric.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "subscription-services", "subscriptions", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
