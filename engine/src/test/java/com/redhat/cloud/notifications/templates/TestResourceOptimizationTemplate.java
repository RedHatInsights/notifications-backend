package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestResourceOptimizationTemplate extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createResourceOptimizationAction();

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

    @AfterEach
    void afterEach() {
        featureFlipper.setResourceOptimizationManagementEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getApp() {
        return "resource-optimization";
    }

    @Test
    public void testDailyDigestEmailTitle() {
        String result = generateAggregatedEmailSubject(ACTION);
        assertTrue(result.contains("Insights Resource Optimization Daily Summary"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setResourceOptimizationManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailSubject(ACTION);
        assertEquals("Daily digest - Resource Optimization - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyDigestEmailBody() {
        String result = generateAggregatedEmailBody(ACTION);
        assertTrue(result.contains("Today, rules triggered on"));
        assertTrue(result.contains("IDLING"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setResourceOptimizationManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailBody(ACTION);
        assertTrue(result.contains("Today, rules triggered on"));
        assertTrue(result.contains("IDLING"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
