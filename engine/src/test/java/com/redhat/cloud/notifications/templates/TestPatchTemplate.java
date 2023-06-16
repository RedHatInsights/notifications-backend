package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPatchTemplate extends EmailTemplatesInDbHelper {

    static final String NEW_ADVISORY = "new-advisory";

    private static final Action ACTION = PatchTestHelpers.createPatchAction();

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

    @AfterEach
    void afterEach() {
        featureFlipper.setPatchEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getApp() {
        return "patch";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(NEW_ADVISORY);
    }

    @Test
    public void testNewAdvisoryEmailTitle() {
        String result = generateEmailSubject(NEW_ADVISORY, ACTION);
        assertTrue(result.contains("Red Hat has recently released new advisories affecting your systems"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(NEW_ADVISORY, ACTION);
        assertEquals("Instant notification - New advisories affecting your systems - Patch - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testNewAdvisoryEmailBody() {
        String result = generateEmailBody(NEW_ADVISORY, ACTION);
        assertTrue(result.contains("Red Hat Insights has just released new Advisories for your organization"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(NEW_ADVISORY, ACTION);
        assertTrue(result.contains("Red Hat Insights has just released new Advisories for your organization"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyDigestEmailTitle() {
        String result = generateAggregatedEmailSubject(ACTION);
        assertTrue(result.contains("Insights Patch advisories daily summary"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailSubject(ACTION);
        assertEquals("Daily digest - Patch - Red Hat Enterprise Linux", result);
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
        assertTrue(result.contains("Here is your Patch advisories summary affecting your systems"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        migrate();
        result = generateAggregatedEmailBody(aggregator.getContext());
        assertTrue(result.contains("There are 4 new advisories affecting your systems."));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
