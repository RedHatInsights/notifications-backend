package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSourcesTemplate extends EmailTemplatesInDbHelper {

    static final String AVAILABILITY_STATUS = "availability-status";
    private static final Action ACTION = TestHelpers.createSourcesAction();

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
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testAvailabilityStatusEmailTitle() {
        String result = generateEmailSubject(AVAILABILITY_STATUS, ACTION);
        assertEquals("Instant notification - Availability Status Change - Sources - Console", result);
    }
}
