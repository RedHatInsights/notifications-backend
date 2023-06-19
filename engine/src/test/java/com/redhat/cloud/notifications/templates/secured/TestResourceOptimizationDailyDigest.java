package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TestResourceOptimizationDailyDigest extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createResourceOptimizationAction();

    @Test
    void testSecureTemplate() {

        String resultSubject = generateAggregatedEmailSubject(ACTION);
        assertEquals("Daily digest - Resource Optimization - Red Hat Enterprise Linux", resultSubject);

        String resultBody = generateAggregatedEmailBody(ACTION);
        assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(resultBody.contains("Today, rules triggered on"));
        assertTrue(resultBody.contains("IDLING"));
    }

    @Override
    protected String getApp() {
        return "resource-optimization";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }
}
