package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestResourceOptimizationTemplate extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createResourceOptimizationAction();

    @Override
    protected String getApp() {
        return "resource-optimization";
    }

    @Test
    public void testDailyDigestEmailTitle() {
        String result = generateAggregatedEmailSubject(ACTION);
        assertEquals("Daily digest - Resource Optimization - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testDailyDigestEmailBody() {
        String result = generateAggregatedEmailBody(ACTION);
        assertTrue(result.contains("Today, rules triggered on"));
        assertTrue(result.contains("IDLING"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
