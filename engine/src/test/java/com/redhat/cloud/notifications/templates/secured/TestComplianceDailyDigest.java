package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TestComplianceDailyDigest extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createComplianceAction();


    @Test
    void testSecureTemplate() {

        String resultBody = generateAggregatedEmailBody(ACTION);
        assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
        assertTrue(resultBody.contains("Red Hat Insights has identified one or more systems"));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Override
    protected String getApp() {
        return "compliance";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }
}
