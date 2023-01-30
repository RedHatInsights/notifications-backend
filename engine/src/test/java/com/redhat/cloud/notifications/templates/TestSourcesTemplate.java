package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.Map;

import static com.redhat.cloud.notifications.templates.Sources.AVAILABILITY_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSourcesTemplate {

    private static final boolean SHOULD_WRITE_ON_FILE_FOR_DEBUG = true;
    private static final Action ACTION = TestHelpers.createSourcesAction();

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    Sources sources;

    @BeforeEach
    void beforeEach() {
        featureFlipper.setSourcesEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testAvailabilityStatusEmailBody() {

        TemplateInstance template = sources.getBody(AVAILABILITY_STATUS, EmailSubscriptionType.INSTANT);
        String result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());
        assertTrue(result.contains("availability status was changed"));

        // test template V2
        featureFlipper.setSourcesEmailTemplatesV2Enabled(true);
        template = sources.getBody(AVAILABILITY_STATUS, EmailSubscriptionType.INSTANT);
        result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());
        assertTrue(result.contains("availability status was changed"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testAvailabilityStatusEmailTitle() {
        TemplateInstance template = sources.getTitle(AVAILABILITY_STATUS, EmailSubscriptionType.INSTANT);
        String result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());
        assertTrue(result.startsWith("Availability Status Change"));

        // test template V2
        featureFlipper.setSourcesEmailTemplatesV2Enabled(true);
        template = sources.getTitle(AVAILABILITY_STATUS, EmailSubscriptionType.INSTANT);
        result = generateEmail(template);
        writeEmailTemplate(result, template.getTemplate().getId());
        assertEquals("Instant notification - Availability Status Change- Sources - Console", result);
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
