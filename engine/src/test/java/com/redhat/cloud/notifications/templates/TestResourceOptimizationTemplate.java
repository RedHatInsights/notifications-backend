package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Environment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestResourceOptimizationTemplate {


    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = true;

    private static final Action ACTION = TestHelpers.createResourceOptimizationAction();

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    ResourceOptimization resourceOptimization;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setResourceOptimizationManagementEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testDailyDigestEmailTitle() {
        String result = generateEmail(resourceOptimization.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY));
        writeEmailTemplate(result, resourceOptimization.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
        assertTrue(result.contains("Insights Resource Optimization Daily Summary"));

        // test template V2
        featureFlipper.setResourceOptimizationManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(resourceOptimization.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY));
        writeEmailTemplate(result, resourceOptimization.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
        assertEquals("Daily digest - Resource Optimization - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyDigestEmailBody() {
        String result =  generateEmail(resourceOptimization.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY));
        writeEmailTemplate(result, resourceOptimization.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
        assertTrue(result.contains("Today, rules triggered on"));
        assertTrue(result.contains("IDLING"));

        // test template V2
        featureFlipper.setResourceOptimizationManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(resourceOptimization.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY));
        writeEmailTemplate(result, resourceOptimization.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
        assertTrue(result.contains("Today, rules triggered on"));
        assertTrue(result.contains("IDLING"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private String generateEmail(TemplateInstance template) {
        return template
            .data("action", ACTION)
            .data("environment", environment)
            .data("user", Map.of("firstName", "Patch User", "lastName", "RHEL"))
            .render();
    }

    public void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }
}
