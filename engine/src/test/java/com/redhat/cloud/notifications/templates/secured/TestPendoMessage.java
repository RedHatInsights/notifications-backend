package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.DriftTestHelpers;
import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.processors.email.EmailPendo;
import com.redhat.cloud.notifications.processors.email.aggregators.DriftEmailPayloadAggregator;
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
        return "drift";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }

    @Test
    void testSecureTemplate() {
        DriftEmailPayloadAggregator aggregator = new DriftEmailPayloadAggregator();

        LocalDateTime startTime = LocalDateTime.of(2021, 4, 22, 13, 15, 33);
        LocalDateTime endTime = LocalDateTime.of(2021, 4, 22, 14, 15, 33);

        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_01", "baseline_1", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_02", "baseline_2", "host-01", "Machine 1"));
        aggregator.aggregate(DriftTestHelpers.createEmailAggregation("rhel", "drift", "baseline_03", "baseline_3", "host-01", "Machine 1"));
        Map<String, Object> drift = aggregator.getContext();
        drift.put("start_time", startTime.toString());
        drift.put("end_time", endTime.toString());

        String resultSubject = generateAggregatedEmailSubject(drift);
        assertEquals("Daily digest - Drift - Red Hat Enterprise Linux", resultSubject);

        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, GENERAL_PENDO_MESSAGE);

        String resultBody = generateAggregatedEmailBody(drift, null);
        commonValidations(resultBody);
        assertFalse(resultBody.contains(emailPendo.getPendoTitle()));
        assertFalse(resultBody.contains(emailPendo.getPendoMessage()));

        resultBody = generateAggregatedEmailBody(drift, emailPendo);
        commonValidations(resultBody);
        assertTrue(resultBody.contains(emailPendo.getPendoTitle()));
        assertTrue(resultBody.contains(emailPendo.getPendoMessage()));
    }

    private static void commonValidations(String resultBody) {
        assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(resultBody.contains("baseline_01"));
        assertTrue(resultBody.contains("baseline_02"));
        assertTrue(resultBody.contains("baseline_03"));
        assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
