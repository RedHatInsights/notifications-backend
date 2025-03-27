package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.PatchTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import com.redhat.cloud.notifications.processors.email.aggregators.PatchEmailPayloadAggregator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Map;

import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_MESSAGE;
import static com.redhat.cloud.notifications.processors.email.EmailPendoResolver.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPendoMessage extends EmailTemplatesInDbHelper  {

    @Override
    protected String getApp() {
        return "patch";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }

    @Test
    void testSecureTemplate() {
        PatchEmailPayloadAggregator aggregator = new PatchEmailPayloadAggregator();

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_1", "test synopsis", "security", "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_2", "test synopsis", "enhancement", "host-01"));
        aggregator.aggregate(PatchTestHelpers.createEmailAggregation("rhel", "patch", "advisory_3", "test synopsis", "enhancement", "host-02"));
        Map<String, Object> patch = aggregator.getContext();
        patch.put("start_time", startTime.toString());
        patch.put("end_time", endTime.toString());

        String resultSubject = generateAggregatedEmailSubject(patch);
        assertEquals("Daily digest - Patch - Red Hat Enterprise Linux", resultSubject);

        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, GENERAL_PENDO_MESSAGE);

        String resultBody = generateAggregatedEmailBody(patch, (EmailPendo) null);
        commonValidations(resultBody);
        assertFalse(resultBody.contains(emailPendo.getPendoTitle()));
        assertFalse(resultBody.contains(emailPendo.getPendoMessage()));

        resultBody = generateAggregatedEmailBody(patch, emailPendo);
        commonValidations(resultBody);
        assertTrue(resultBody.contains(emailPendo.getPendoTitle()));
        assertTrue(resultBody.contains(emailPendo.getPendoMessage()));
    }

    private static void commonValidations(String resultBody) {
        assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(resultBody.contains("advisory_1"));
        assertTrue(resultBody.contains("advisory_2"));
        assertTrue(resultBody.contains("advisory_3"));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
