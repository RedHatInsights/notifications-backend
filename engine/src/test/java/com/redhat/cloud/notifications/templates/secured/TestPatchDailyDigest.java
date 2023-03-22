package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.DAILY;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TestPatchDailyDigest extends EmailTemplatesInDbHelper {

    private final String enhancement = "enhancement";
    private final String bugfix = "bugfix";
    private final String security = "security";

    @Test
    void testSecureTemplate() {
        PatchEmailPayloadAggregator aggregator = new PatchEmailPayloadAggregator();
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_1", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_2", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_3", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_4", bugfix, "host-03"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_5", bugfix, "host-04"));

        statelessSessionFactory.withSession(statelessSession -> {
            AggregationEmailTemplate emailTemplate = templateRepository.findAggregationEmailTemplate(getBundle(), getApp(), DAILY).get();

            TemplateInstance subjectTemplate = templateService.compileTemplate(emailTemplate.getSubjectTemplate().getData(), emailTemplate.getSubjectTemplate().getName());
            String resultSubject = generateEmail(subjectTemplate, aggregator.getContext());
            assertEquals("Daily digest - Patch - Red Hat Enterprise Linux", resultSubject);

            TemplateInstance bodyTemplate = templateService.compileTemplate(emailTemplate.getBodyTemplate().getData(), emailTemplate.getBodyTemplate().getName());

            String resultBody = generateEmail(bodyTemplate, aggregator.getContext());
            writeEmailTemplate(resultBody, bodyTemplate.getTemplate().getId() + ".html");
            assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
            assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
            assertTrue(resultBody.contains("Here is your Patch advisories summary affecting your systems"));
        });
    }

    @Override
    protected String getApp() {
        return "patch";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }
}
