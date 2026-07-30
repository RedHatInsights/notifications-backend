package email;

import com.redhat.cloud.notifications.qute.templates.TemplateService;
import email.pojo.Environment;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that the optional {@code content-header-logo} Qute section added to the 4 base email
 * layout templates falls back to the standard Red Hat logo when not overridden, and can be fully
 * replaced (image and link) when a caller defines the section - the mechanism a future Lightwell
 * template would rely on.
 *
 * The daily digest templates are not {@code #include}d by any real template today (they are
 * registered and rendered directly), so their tests here only prove the section resolves
 * correctly in isolation, not an existing production code path.
 */
@QuarkusTest
class TestEmailHeaderLogoSection {

    private static final String DEFAULT_LOGO_FILENAME = "Logo-Red_Hat-Hybrid_Cloud_Console-A-Reverse-RGB.png";
    private static final String LIGHTWELL_LOGO_FILENAME = "lightwell-logo.png";
    private static final String LIGHTWELL_LOGO_OVERRIDE = "{#content-header-logo}"
        + "<a href=\"{environment.url}\" target=\"_blank\">"
        + "<img src=\"https://example.com/" + LIGHTWELL_LOGO_FILENAME + "\" alt=\"Lightwell logo\" width=\"340\" />"
        + "</a>"
        + "{/content-header-logo}";

    @Inject
    TemplateService templateService;

    @Inject
    Environment environment;

    @Test
    void testInstantEmailBodyDefaultLogo() {
        assertDefaultLogo(render(instantEmailSnippet("email/Common/insightsEmailBody", "")));
    }

    @Test
    void testInstantEmailBodyLogoOverride() {
        assertOverriddenLogo(render(instantEmailSnippet("email/Common/insightsEmailBody", LIGHTWELL_LOGO_OVERRIDE)));
    }

    @Test
    void testSecureInstantEmailBodyDefaultLogo() {
        assertDefaultLogo(render(instantEmailSnippet("email/Secure/Common/insightsEmailBody", "")));
    }

    @Test
    void testSecureInstantEmailBodyLogoOverride() {
        assertOverriddenLogo(render(instantEmailSnippet("email/Secure/Common/insightsEmailBody", LIGHTWELL_LOGO_OVERRIDE)));
    }

    @Test
    void testDailyEmailBodyDefaultLogo() {
        assertDefaultLogo(render(dailyEmailSnippet("email/Common/insightsDailyEmailBody", "")));
    }

    @Test
    void testDailyEmailBodyLogoOverride() {
        assertOverriddenLogo(render(dailyEmailSnippet("email/Common/insightsDailyEmailBody", LIGHTWELL_LOGO_OVERRIDE)));
    }

    @Test
    void testSecureDailyEmailBodyDefaultLogo() {
        assertDefaultLogo(render(dailyEmailSnippet("email/Secure/Common/insightsDailyEmailBody", "")));
    }

    @Test
    void testSecureDailyEmailBodyLogoOverride() {
        assertOverriddenLogo(render(dailyEmailSnippet("email/Secure/Common/insightsDailyEmailBody", LIGHTWELL_LOGO_OVERRIDE)));
    }

    private void assertDefaultLogo(String result) {
        assertTrue(result.contains(DEFAULT_LOGO_FILENAME));
        assertFalse(result.contains(LIGHTWELL_LOGO_FILENAME));
    }

    private void assertOverriddenLogo(String result) {
        assertTrue(result.contains(LIGHTWELL_LOGO_FILENAME));
        assertFalse(result.contains(DEFAULT_LOGO_FILENAME));
        // the override keeps {environment.url} as the click-through link - confirm it still resolved
        assertTrue(result.contains("href=\"" + environment.url() + "\""));
    }

    private String instantEmailSnippet(String templatePath, String logoOverride) {
        return "{@boolean renderTitleRightPart=true}"
            + "{#include " + templatePath + "}"
            + logoOverride
            + "{#content-header-title}Test{/content-header-title}"
            + "{#content-title}Test{/content-title}"
            + "{#content-title-right-part}1{/content-title-right-part}"
            + "{#content-body}Test body{/content-body}"
            + "{#content-button-href}https://example.com{/content-button-href}"
            + "{#content-button-service-name}Test Service{/content-button-service-name}"
            + "{/include}";
    }

    private String dailyEmailSnippet(String templatePath, String logoOverride) {
        return "{#include " + templatePath + "}" + logoOverride + "{/include}";
    }

    private String render(String templateContent) {
        Map<String, Object> context = new HashMap<>();
        context.put("environment", environment);
        context.put("pendo_message", null);
        context.put("action", Map.of(
            "orgId", "12345",
            "context", Map.of("title", "Daily digest", "orgId", "12345", "items", List.of())
        ));
        return templateService.renderTemplateWithCustomDataMap(templateContent, context);
    }
}
