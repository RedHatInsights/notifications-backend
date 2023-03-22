package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.List;

import static com.redhat.cloud.notifications.templates.Sources.AVAILABILITY_STATUS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSourcesTemplate extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createSourcesAction();

    @Inject
    FeatureFlipper featureFlipper;

    @AfterEach
    void afterEach() {
        featureFlipper.setSourcesEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Override
    protected String getBundle() {
        return "console";
    }

    @Override
    protected String getApp() {
        return "sources";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(AVAILABILITY_STATUS);
    }

    @Test
    public void testAvailabilityStatusEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(AVAILABILITY_STATUS, ACTION);
            assertTrue(result.contains("availability status was changed"));

            featureFlipper.setSourcesEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(AVAILABILITY_STATUS, ACTION);
            assertTrue(result.contains("availability status was changed"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testAvailabilityStatusEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(AVAILABILITY_STATUS, ACTION);
            assertTrue(result.startsWith("Availability Status Change"));

            featureFlipper.setSourcesEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(AVAILABILITY_STATUS, ACTION);
            assertEquals("Instant notification - Availability Status Change - Sources - Console", result);
        });
    }
}
