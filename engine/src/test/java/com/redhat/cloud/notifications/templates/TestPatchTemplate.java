package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPatchTemplate {

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = false;

    private static final Action ACTION = PatchTestHelpers.createPatchAction();

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Patch patch;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setPatchEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testNewAdvisoryEmailTitle() {
        String result = generateEmail(patch.getTitle(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, patch.getTitle(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains("Red Hat has recently released new advisories affecting your systems"));

        // test template V2
        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        result = generateEmail(patch.getTitle(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, patch.getTitle(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertEquals("Instant notification - New advisories affecting your systems - Patch - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testNewAdvisoryEmailBody() {
        String result =  generateEmail(patch.getBody(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, patch.getBody(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains("Red Hat Insights has just released new Advisories for your organization"));

        // test template V2
        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        result = generateEmail(patch.getBody(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT));
        writeEmailTemplate(result, patch.getBody(Patch.NEW_ADVISORY, EmailSubscriptionType.INSTANT).getTemplate().getId());
        assertTrue(result.contains("Red Hat Insights has just released new Advisories for your organization"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyDigestEmailTitle() {
        String result = generateEmail(patch.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY));
        writeEmailTemplate(result, patch.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
        assertTrue(result.contains("Insights Patch advisories daily summary"));

        // test template V2
        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        result = generateEmail(patch.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY));
        writeEmailTemplate(result, patch.getTitle(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
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
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_1", security, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_2", enhancement, "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_3", enhancement, "host-02"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation(bundle, application, "advisory_4", bugfix, "host-03"));

        String result =  generateEmail(patch.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY), aggregator.getContext());
        writeEmailTemplate(result, patch.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
        assertTrue(result.contains("Here is your Patch advisories summary affecting your systems"));

        // test template V2
        featureFlipper.setPatchEmailTemplatesV2Enabled(true);
        result = generateEmail(patch.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY), aggregator.getContext());
        writeEmailTemplate(result, patch.getBody(RandomStringUtils.randomAlphabetic(10), EmailSubscriptionType.DAILY).getTemplate().getId());
        assertTrue(result.contains("Here is your Patch advisories summary affecting your systems"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private String generateEmail(TemplateInstance template) {
        return template
            .data("action", ACTION)
            .data("environment", environment)
            .data("user", Map.of("firstName", "Patch User", "lastName", "RHEL"))
            .render();
    }

    private String generateEmail(TemplateInstance template,  Map<String, Object> context) {
        context.put("start_time", LocalDateTime.now());
        context.put("end_time", LocalDateTime.now());
        return template
            .data("action", Map.of(
                "context", context,
                "timestamp", LocalDateTime.now(),
                "bundle", "rhel"
            ))
            .data("environment", environment)
            .data("user", Map.of("firstName", "Patch User", "lastName", "RHEL"))
            .render();
    }

    public void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }
}
