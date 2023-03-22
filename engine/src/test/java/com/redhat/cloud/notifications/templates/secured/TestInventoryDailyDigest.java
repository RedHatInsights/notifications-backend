package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.InventoryTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.processors.email.aggregators.InventoryEmailAggregator;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TestInventoryDailyDigest extends EmailTemplatesInDbHelper {

    @Test
    void testSecureTemplate() {
        InventoryEmailAggregator aggregator = new InventoryEmailAggregator();
        aggregator.aggregate(InventoryTestHelpers.createEmailAggregation("tenant", "rhel", "inventory", "test event"));

        statelessSessionFactory.withSession(statelessSession -> {
            // App: compliance
            AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();

            TemplateInstance subjectTemplate = templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
            String resultSubject = generateEmail(subjectTemplate, aggregator.getContext());
            assertEquals("Daily digest - Inventory - Red Hat Enterprise Linux", resultSubject);

            TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());

            String resultBody = generateEmail(bodyTemplate, aggregator.getContext());
            writeEmailTemplate(resultBody, bodyTemplate.getTemplate().getId() + ".html");
            assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
            assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));

            assertFalse(resultBody.contains(InventoryTestHelpers.displayName1), "Body should not contain host display name" + InventoryTestHelpers.displayName1);
            assertFalse(resultBody.contains(InventoryTestHelpers.errorMessage1), "Body should not contain error message" + InventoryTestHelpers.errorMessage1);
        });
    }

    @Override
    protected String getApp() {
        return "inventory";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }
}
