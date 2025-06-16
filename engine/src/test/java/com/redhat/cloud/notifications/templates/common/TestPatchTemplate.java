package com.redhat.cloud.notifications.templates.common;

import com.redhat.cloud.notifications.EmailTemplatesRendererHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPatchTemplate extends EmailTemplatesRendererHelper {

    static final String NEW_ADVISORY = "new-advisory";

    private static final Action ACTION = PatchTestHelpers.createPatchAction();

    @Override
    protected String getApp() {
        return "patch";
    }

    @Override
    protected String getAppDisplayName() {
        return "Patch";
    }

    @Test
    public void testNewAdvisoryEmailTitle() {
        eventTypeDisplayName = "New advisory";
        String result = generateEmailSubject(NEW_ADVISORY, ACTION);
        assertEquals("Instant notification - New advisory - Patch - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testNewAdvisoryEmailBody() {
        String result = generateEmailBody(NEW_ADVISORY, ACTION);
        assertTrue(result.contains("Red Hat Insights has just released new Advisories for your organization"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyDigestEmailBody() {
        PatchEmailPayloadAggregator aggregator = new PatchEmailPayloadAggregator();
        String bundle = "rhel";
        String application = "patch";
        String enhancement = "enhancement";
        String bugfix = "bugfix";
        String security = "security";
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_1", "synopsis", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_2", "synopsis", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_3", "synopsis", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_4", "synopsis", bugfix, "host-03"));
        aggregator.setStartTime(LocalDateTime.now());
        aggregator.setEndTimeKey(LocalDateTime.now());

        String result = generateAggregatedEmailBody(aggregator.getContext());
        assertTrue(result.contains("There are 4 new advisories affecting your systems."));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
