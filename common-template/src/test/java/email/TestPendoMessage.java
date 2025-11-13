package email;

import com.redhat.cloud.notifications.ingress.Action;
import email.pojo.EmailPendo;
import helpers.PatchTestHelpers;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static email.pojo.EmailPendo.GENERAL_PENDO_MESSAGE;
import static email.pojo.EmailPendo.GENERAL_PENDO_TITLE;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestPendoMessage extends EmailTemplatesRendererHelper {

    private static final String EVENT_TYPE_NAME = "new-advisory";

    @Override
    protected String getApp() {
        return "patch";
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantEmailBody(boolean useBetaTemplate) {
        EmailPendo emailPendo = new EmailPendo(GENERAL_PENDO_TITLE, String.format(GENERAL_PENDO_MESSAGE, environment.url()));

        Action action = PatchTestHelpers.createPatchAction();
        String result = generateEmailBody(EVENT_TYPE_NAME, action, useBetaTemplate);
        commonValidations(result);
        assertFalse(result.contains(emailPendo.getPendoTitle()));
        assertFalse(result.contains(emailPendo.getPendoMessage()));

        result = generateEmailBody(EVENT_TYPE_NAME, action, emailPendo, false, useBetaTemplate);
        commonValidations(result);
        assertTrue(result.contains(emailPendo.getPendoTitle()));
        assertTrue(result.contains(emailPendo.getPendoMessage()));
    }

    private static void commonValidations(String result) {
        assertTrue(result.contains("name 1"));
        assertTrue(result.contains("synopsis 2"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
