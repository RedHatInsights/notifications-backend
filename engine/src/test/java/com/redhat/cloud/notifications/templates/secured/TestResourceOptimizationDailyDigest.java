package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TestResourceOptimizationDailyDigest extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createResourceOptimizationAction();

    @Test
    void testSecureTemplate() {

        statelessSessionFactory.withSession(statelessSession -> {
            AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();

            TemplateInstance subjectTemplate = templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
            String resultSubject = generateEmail(subjectTemplate, ACTION);
            assertEquals("Daily digest - Resource Optimization - Red Hat Enterprise Linux", resultSubject);

            TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());

            String resultBody = generateEmail(bodyTemplate, ACTION);
            writeEmailTemplate(resultBody, bodyTemplate.getTemplate().getId() + ".html");
            assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
            assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
            assertTrue(resultBody.contains("Today, rules triggered on"));
            assertTrue(resultBody.contains("IDLING"));
        });
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
