package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

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
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_1", "synopsis", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_2", "synopsis", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_3", "synopsis", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_4", "synopsis", bugfix, "host-03"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(getBundle(), getApp(), "advisory_5", "synopsis", bugfix, "host-04"));

        String resultSubject = generateAggregatedEmailSubject(aggregator.getContext());
        assertEquals("Daily digest - Patch - Red Hat Enterprise Linux", resultSubject);

        String resultBody = generateAggregatedEmailBody(aggregator.getContext());
        assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(resultBody.contains("new advisories affecting your systems"));
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
