package email;

import com.redhat.cloud.notifications.ingress.Action;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testAvailabilityStatusEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(AVAILABILITY_STATUS, ACTION, useBetaTemplate);
        if (useBetaTemplate) {
            assertTrue(result.contains("The availability of integration"));
        } else {
            assertTrue(result.contains("availability status was changed"));
        }
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testAvailabilityStatusEmailTitle() {
        eventTypeDisplayName = "Availability Status";
        String result = generateEmailSubject(AVAILABILITY_STATUS, ACTION);
        assertEquals("Instant notification - Availability Status - Sources - Console", result);
    }
}
