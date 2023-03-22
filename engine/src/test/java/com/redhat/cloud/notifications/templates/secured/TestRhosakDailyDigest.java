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
public class TestRhosakDailyDigest extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createRhosakAction();

    @Test
    void testSecureTemplate() {

        statelessSessionFactory.withSession(statelessSession -> {
            String resultSubject = generateAggregatedEmailSubject(ACTION);
            assertEquals("Daily digest - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", resultSubject);

            String resultBody = generateAggregatedEmailBody(ACTION);
            assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
            assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Override
    protected String getApp() {
        return "rhosak";
    }

    @Override
    protected String getBundle() {
        return "application-services";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }
}
