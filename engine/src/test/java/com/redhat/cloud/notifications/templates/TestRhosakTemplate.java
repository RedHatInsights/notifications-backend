package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestRhosakTemplate extends EmailTemplatesInDbHelper {

    static final String SCHEDULED_UPGRADE = "scheduled-upgrade";
    static final String DISRUPTION = "disruption";
    static final String INSTANCE_CREATED = "instance-created";
    static final String INSTANCE_DELETED = "instance-deleted";
    static final String ACTION_REQUIRED = "action-required";
    private static final Action ACTION = TestHelpers.createRhosakAction();

    @Inject
    FeatureFlipper featureFlipper;

    @AfterEach
    void afterEach() {
        featureFlipper.setRhosakEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getBundle() {
        return "application-services";
    }

    @Override
    protected String getApp() {
        return "rhosak";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(SCHEDULED_UPGRADE, DISRUPTION, INSTANCE_CREATED, INSTANCE_DELETED, ACTION_REQUIRED);
    }

    @Test
    public void testDailyEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateAggregatedEmailBody(ACTION);
            assertFalse(result.contains(TestHelpers.HCC_LOGO_TARGET));

            featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
            migrate();
            result = generateAggregatedEmailBody(ACTION);
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testDailyEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateAggregatedEmailSubject(ACTION);
            assertEquals("Red Hat OpenShift Streams for Apache Kafka Daily Report", result);

            featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
            migrate();
            result = generateAggregatedEmailSubject(ACTION);
            assertEquals("Daily digest - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", result);
        });
    }

    @ValueSource(strings = { SCHEDULED_UPGRADE, DISRUPTION, INSTANCE_CREATED, INSTANCE_DELETED, ACTION_REQUIRED })
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateTitles(String eventType) {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(eventType, ACTION);
            testTitle(eventType, result);

            featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(eventType, ACTION);
            testTitle(eventType, result);
        });
    }

    private void testTitle(String eventType, String result) {
        switch (eventType) {
            case SCHEDULED_UPGRADE:
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Scheduled Upgrade - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", result);
                } else {
                    assertTrue(result.startsWith("Upgrade notification"));
                }
                break;
            case DISRUPTION:
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Service Disruption - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", result);
                } else {
                    assertTrue(result.contains("Service Disruption for your"));
                }
                break;
            case INSTANCE_CREATED:
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Instance Created - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", result);
                } else {
                    assertTrue(result.contains("Your OpenShift Streams instance"));
                    assertTrue(result.contains("has been created"));
                }
                break;
            case INSTANCE_DELETED:
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Instance Deleted - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", result);
                } else {
                    assertTrue(result.startsWith("Your OpenShift Streams instance"));
                    assertTrue(result.contains("has been deleted"));
                }
                break;
            case ACTION_REQUIRED:
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertEquals("Instant notification - Action Required - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", result);
                } else {
                    assertTrue(result.startsWith("Action Required for your"));
                }
                break;
            default:
                break;
        }
    }

    @ValueSource(strings = { SCHEDULED_UPGRADE, DISRUPTION, INSTANCE_CREATED, INSTANCE_DELETED, ACTION_REQUIRED })
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateBodies(String eventType) {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(eventType, ACTION);
            testBody(eventType, result);

            featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(eventType, ACTION);
            testBody(eventType, result);
        });
    }

    private void testBody(String eventType, String result) {
        switch (eventType) {
            case SCHEDULED_UPGRADE:
                assertTrue(result.contains("This notification is for your OpenShift Streams instances listed below"));
                assertTrue(result.contains("Maximum throughput of an OpenShift Streams instance might briefly "));
                assertTrue(result.contains("What should you do to minimize impact"));
                assertTrue(result.contains("For guidance on working with Red Hat Technical Support"));
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case DISRUPTION:
                assertTrue(result.contains("Service Disruption"));
                assertTrue(result.contains("Our monitoring systems have identified a service disruption that’s impacting"));
                assertTrue(result.contains(" For guidance on working with Red Hat Technical Support"));
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case INSTANCE_CREATED:
                assertTrue(result.contains("has been successfully created"));
                assertTrue(result.contains("The bootstrap server host and port of your OpenShift Streams instance are"));
                assertTrue(result.contains("For guidance on working with Red Hat Technical Support"));
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case INSTANCE_DELETED:
                assertTrue(result.contains("has been successfully deleted"));
                assertTrue(result.contains("Go to Kafka Instances page"));
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            case ACTION_REQUIRED:
                assertTrue(result.contains("This notification is for your OpenShift Streams instances listed below"));
                assertTrue(result.contains("For guidance on working with Red Hat Technical Support"));
                if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
                    assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
                }
                break;
            default:
                break;
        }
    }
}
