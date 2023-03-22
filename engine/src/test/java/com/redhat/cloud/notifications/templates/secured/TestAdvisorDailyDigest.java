package com.redhat.cloud.notifications.templates.secured;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.Map;

import static com.redhat.cloud.notifications.AdvisorTestHelpers.createEmailAggregation;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_1;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_2;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_3;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_4;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregatorTest.TEST_RULE_6;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class TestAdvisorDailyDigest extends EmailTemplatesInDbHelper {

    @Test
    void testSecureTemplate() {
        AdvisorEmailAggregator aggregator = new AdvisorEmailAggregator();
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_1));
        aggregator.aggregate(createEmailAggregation(NEW_RECOMMENDATION, TEST_RULE_6));
        aggregator.aggregate(createEmailAggregation(RESOLVED_RECOMMENDATION, TEST_RULE_2));
        aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_3));
        aggregator.aggregate(createEmailAggregation(DEACTIVATED_RECOMMENDATION, TEST_RULE_4));

        Map<String, Object> context = aggregator.getContext();
        context.put("start_time", LocalDateTime.now().toString());
        context.put("end_time", LocalDateTime.now().toString());

        statelessSessionFactory.withSession(statelessSession -> {

            String resultSubject = generateAggregatedEmailSubject(context);
            assertEquals("Daily digest - Advisor - Red Hat Enterprise Linux", resultSubject);

            String resultBody = generateAggregatedEmailBody(context);
            assertTrue(resultBody.contains(COMMON_SECURED_LABEL_CHECK));
            assertTrue(resultBody.contains(TestHelpers.HCC_LOGO_TARGET));
            assertTrue(resultBody.contains("New Recommendations"));
            assertTrue(resultBody.contains("/insights/advisor/recommendations/test|Active_rule_1"));
            assertTrue(resultBody.contains("Active rule 1</a>"));
            assertTrue(resultBody.contains("https://console.redhat.com/apps/frontend-assets/email-assets/img_incident.png"));
            assertTrue(resultBody.contains("/apps/frontend-assets/email-assets/img_important.png"));
            assertTrue(resultBody.contains("Resolved Recommendation"));
            assertTrue(resultBody.contains("/insights/advisor/recommendations/test|Active_rule_2"));
            assertTrue(resultBody.contains("Active rule 2</a>"));
            assertTrue(resultBody.contains("/apps/frontend-assets/email-assets/img_low.png"));
            assertTrue(resultBody.contains("Deactivated Recommendations"));
            assertTrue(resultBody.contains("/insights/advisor/recommendations/test|Active_rule_3"));
            assertTrue(resultBody.contains("Active rule 3</a>"));
            assertTrue(resultBody.contains("/apps/frontend-assets/email-assets/img_critical.png"));
        });
    }

    @Override
    protected String getApp() {
        return "advisor";
    }

    @Override
    protected Boolean useSecuredTemplates() {
        return true;
    }
}
