package email;

import com.redhat.cloud.notifications.ingress.Action;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestSourcesTemplate extends EmailTemplatesRendererHelper {

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
    protected String getBundleDisplayName() {
        return "Console";
    }

    @Override
    protected String getAppDisplayName() {
        return "Sources";
    }

    @Test
    public void testAvailabilityStatusEmailBody() {
        String result = generateEmailBody(AVAILABILITY_STATUS, ACTION);
        assertTrue(result.contains("availability status was changed"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testAvailabilityStatusEmailTitle() {
        eventTypeDisplayName = "Availability Status";
        String result = generateEmailSubject(AVAILABILITY_STATUS, ACTION);
        assertEquals("Instant notification - Availability Status - Sources - Console", result);
    }
}
