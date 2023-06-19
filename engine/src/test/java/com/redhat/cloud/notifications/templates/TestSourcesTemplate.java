package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSourcesTemplate extends EmailTemplatesInDbHelper {

    static final String AVAILABILITY_STATUS = "availability-status";
    private static final Action ACTION = TestHelpers.createSourcesAction();

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

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
        String result = generateEmailBody(AVAILABILITY_STATUS, ACTION);
        assertTrue(result.contains("availability status was changed"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setSourcesEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(AVAILABILITY_STATUS, ACTION);
        assertTrue(result.contains("availability status was changed"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testAvailabilityStatusEmailTitle() {
        String result = generateEmailSubject(AVAILABILITY_STATUS, ACTION);
        assertTrue(result.startsWith("Availability Status Change"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setSourcesEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(AVAILABILITY_STATUS, ACTION);
        assertEquals("Instant notification - Availability Status Change - Sources - Console", result);
    }
}
