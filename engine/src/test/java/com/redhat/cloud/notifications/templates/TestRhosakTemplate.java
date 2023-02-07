package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import javax.inject.Inject;
import java.util.Map;

import static com.redhat.cloud.notifications.templates.Rhosak.ACTION_REQUIRED;
import static com.redhat.cloud.notifications.templates.Rhosak.DISRUPTION;
import static com.redhat.cloud.notifications.templates.Rhosak.INSTANCE_CREATED;
import static com.redhat.cloud.notifications.templates.Rhosak.INSTANCE_DELETED;
import static com.redhat.cloud.notifications.templates.Rhosak.SCHEDULED_UPGRADE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestRhosakTemplate {

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = false;
    private static final Action ACTION = TestHelpers.createRhosakAction();

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Rhosak rhosak;

    @AfterEach
    void afterEach() {
        featureFlipper.setRhosakEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testDailyEmailBody() {

        TemplateInstance template = rhosak.getBody(null, EmailSubscriptionType.DAILY);
        String result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());

        // test template V2
        featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
        template = rhosak.getBody(null, EmailSubscriptionType.DAILY);
        result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testDailyEmailTitle() {
        TemplateInstance template = rhosak.getTitle(null, EmailSubscriptionType.DAILY);
        String result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());
        assertEquals("Red Hat OpenShift Streams for Apache Kafka Daily Report", result);

        // test template V2
        featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
        template = rhosak.getTitle(null, EmailSubscriptionType.DAILY);
        result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());
        assertEquals("Daily digest - Red Hat OpenShift Streams for Apache Kafka - Application and Data Services", result);
    }

    @ValueSource(strings = { SCHEDULED_UPGRADE, DISRUPTION, INSTANCE_CREATED, INSTANCE_DELETED, ACTION_REQUIRED })
    @ParameterizedTest
    void shouldTestAllEventTypeTemplateTitles(String eventType) {
        TemplateInstance templateInstance = rhosak.getTitle(eventType, EmailSubscriptionType.INSTANT);
        String result = generateEmail(templateInstance);
        writeEmailTemplate(result, templateInstance.getTemplate().getId());
        testTitle(eventType, result);

        featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
        templateInstance = rhosak.getTitle(eventType, EmailSubscriptionType.INSTANT);
        result = generateEmail(templateInstance);
        writeEmailTemplate(result, templateInstance.getTemplate().getId());
        testTitle(eventType, result);
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
        TemplateInstance templateInstance = rhosak.getBody(eventType, EmailSubscriptionType.INSTANT);
        String result = generateEmail(templateInstance);
        writeEmailTemplate(result, templateInstance.getTemplate().getId());
        testBody(eventType, result);

        featureFlipper.setRhosakEmailTemplatesV2Enabled(true);
        templateInstance = rhosak.getBody(eventType, EmailSubscriptionType.INSTANT);
        result = generateEmail(templateInstance);
        writeEmailTemplate(result, templateInstance.getTemplate().getId());
        testBody(eventType, result);
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

    private String generateEmail(TemplateInstance template) {
        return template
            .data("action", ACTION)
            .data("environment", environment)
            .data("user", Map.of("firstName", "Rhosak User", "lastName", "application-services"))
            .render();
    }

    public void writeEmailTemplate(String result, String fileName) {
        if (SHOULD_WRITE_ON_FILE_FOR_DEBUG) {
            TestHelpers.writeEmailTemplate(result, fileName);
        }
    }
}
