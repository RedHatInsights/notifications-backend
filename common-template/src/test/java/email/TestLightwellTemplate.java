package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.mapping.Lightwell.LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE;
import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestLightwellTemplate extends EmailTemplatesRendererHelper {

    @Override
    protected String getApp() {
        return "lightwell";
    }

    @Override
    protected String getBundle() {
        return "lightwell";
    }

    @Override
    protected String getBundleDisplayName() {
        return "Lightwell";
    }

    @Override
    protected String getAppDisplayName() {
        return "Lightwell";
    }

    static final String LIGHTWELL_LOGO = "Logo_lightwell_dark.png";

    @Test
    public void testJavaRemediatedEmailBody() {
        Action action = createLightwellAction();
        eventTypeDisplayName = "Java Remediated";
        String result = generateEmailBody(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action, false);

        assertTrue(result.contains(LIGHTWELL_LOGO));

        // Plural title, intro text and events counter badge
        assertTrue(result.contains("New Packages Available for Java Remediated"));
        assertTrue(result.contains("These following packages were fixed by Lightwell in the Java Remediated and are available for your access."));
        assertTrue(result.contains(">9</a>"));

        // CTA button
        assertTrue(result.contains("href=\"https://console.redhat.com/lightwell\" target=\"_blank\""));
        assertTrue(result.contains("Go to Lightwell"));

        // Packages
        assertTrue(result.contains("org.glassfish.jaxb:codemodel"));
        assertTrue(result.contains("https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel"));
        assertTrue(result.contains("org.glassfish.jaxb:jaxb-core"));
        assertTrue(result.contains("org.glassfish.jaxb:jaxb-jxc"));
        assertTrue(result.contains("org.glassfish.jaxb:jaxb-runtime"));
        assertTrue(result.contains("org.glassfish.jaxb:jaxb-xjc"));
        assertTrue(result.contains("org.glassfish.jaxb:txw2"));
        assertTrue(result.contains("org.glassfish.jaxb:txwc2"));
        assertTrue(result.contains("org.glassfish.jaxb:xsom"));
        assertTrue(result.contains("org.json:json"));

        // Releases
        assertTrue(result.contains("4.0.4.rhlw003"));
        assertTrue(result.contains("4.0.4.rhlw004"));
        assertTrue(result.contains("5.0.0.rhlw001"));
        assertTrue(result.contains("5.5.5.rhlw001"));
        assertTrue(result.contains("4.1.0.rhlw001"));
        assertTrue(result.contains("20220320.0.0.rhlw-00001"));
        assertTrue(result.contains("20220320.0.0.rhlw-00002"));

        // CVEs and severities
        assertTrue(result.contains("CVE-2026-1234"));
        assertTrue(result.contains("CVE-2026-5678"));
        assertTrue(result.contains("CVE-2026-9999"));
        assertTrue(result.contains("CVE-2026-1111"));
        assertTrue(result.contains("CVE-2026-2222"));
        assertTrue(result.contains("CVE-2026-3333"));
        assertTrue(result.contains("CVE-2026-4242"));
        assertTrue(result.contains("CVE-2026-0909"));
        assertTrue(result.contains(">Critical</td>"));
        assertTrue(result.contains(">Important</td>"));
    }

    @Test
    public void testJavaRemediatedEmailBodySingleEvent() {
        Action action = createLightwellActionWithReleases(List.of(
            buildRelease("org.glassfish.jaxb:codemodel", List.of("4.0.4.rhlw003"), List.of(
                buildCve("CVE-2026-1234", "critical")
            ))
        ));
        eventTypeDisplayName = "Java Remediated";
        String result = generateEmailBody(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action, false);

        assertTrue(result.contains("New Package Available for Java Remediated"));
        assertFalse(result.contains("New Packages Available"));
        assertTrue(result.contains("The following package was fixed by Lightwell in the Java Remediated and is available for your access."));
        assertFalse(result.contains("These following packages were fixed"));
        assertTrue(result.contains(">1</a>"));
    }

    @Test
    public void testJavaRemediatedEmailBodyAllSeverityLevels() {
        Action action = createLightwellActionWithReleases(List.of(
            buildRelease("org.glassfish.jaxb:codemodel", List.of("4.0.4.rhlw003"), List.of(
                buildCve("CVE-2026-1001", "low"),
                buildCve("CVE-2026-1002", "moderate"),
                buildCve("CVE-2026-1003", "important"),
                buildCve("CVE-2026-1004", "critical")
            ))
        ));
        eventTypeDisplayName = "Java Remediated";
        String result = generateEmailBody(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action, false);

        assertTrue(result.contains(">Low</td>"));
        assertTrue(result.contains(">Moderate</td>"));
        assertTrue(result.contains(">Important</td>"));
        assertTrue(result.contains(">Critical</td>"));
        assertTrue(result.contains("alt=\"Low\""));
        assertTrue(result.contains("alt=\"Moderate\""));
        assertTrue(result.contains("alt=\"Important\""));
        assertTrue(result.contains("alt=\"Critical\""));
    }

    @Test
    public void testJavaRemediatedEmailBodyDefaultFooterShowsPreferencesLink() {
        Action action = createLightwellAction();
        eventTypeDisplayName = "Java Remediated";
        String result = generateEmailBody(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action, null, false);

        assertTrue(result.contains("This email was sent by Lightwell | "));
        assertTrue(result.contains("Manage email preferences"));
        assertFalse(result.contains("it is critical or requires action"));
    }

    @Test
    public void testJavaRemediatedEmailBodyIgnoresUserPreferencesFooter() {
        Action action = createLightwellAction();
        eventTypeDisplayName = "Java Remediated";
        String result = generateEmailBody(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action, null, true);

        assertTrue(result.contains("This email was sent by Lightwell, it is critical or requires action."));
        assertFalse(result.contains("Manage email preferences"));
    }

    private static Action createLightwellActionWithReleases(List<Map<String, Object>> releases) {
        Action action = new Action();
        action.setBundle("lightwell");
        action.setApplication("lightwell");
        action.setTimestamp(LocalDateTime.now());
        action.setEventType(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE);
        action.setOrgId(DEFAULT_ORG_ID);
        action.setContext(new Context.ContextBuilder().build());
        action.setEvents(List.of(buildPackageEvent("org.glassfish.jaxb:codemodel", releases)));
        return action;
    }

    private static Action createLightwellAction() {
        Action action = new Action();
        action.setBundle("lightwell");
        action.setApplication("lightwell");
        action.setTimestamp(LocalDateTime.now());
        action.setEventType(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE);
        action.setOrgId(DEFAULT_ORG_ID);
        action.setContext(new Context.ContextBuilder().build());
        action.setEvents(List.of(
            buildPackageEvent("org.glassfish.jaxb:codemodel", List.of(
                buildRelease("org.glassfish.jaxb:codemodel", List.of("4.0.4.rhlw003", "4.0.4.rhlw004"), List.of(
                    buildCve("CVE-2026-1234", "critical"),
                    buildCve("CVE-2026-5678", "critical"),
                    buildCve("CVE-2026-9999", "critical")
                )),
                buildRelease("org.glassfish.jaxb:codemodel", List.of("5.0.0.rhlw001"), List.of(
                    buildCve("CVE-2026-1234", "critical"),
                    buildCve("CVE-2026-5678", "critical"),
                    buildCve("CVE-2026-9999", "critical")
                )),
                buildRelease("org.glassfish.jaxb:codemodel", List.of("5.5.5.rhlw001"), List.of(
                    buildCve("CVE-2026-1234", "critical"),
                    buildCve("CVE-2026-5678", "critical"),
                    buildCve("CVE-2026-9999", "critical")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-core", List.of(
                buildRelease("org.glassfish.jaxb:jaxb-core", List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-1111", "important"),
                    buildCve("CVE-2026-2222", "important"),
                    buildCve("CVE-2026-9999", "critical")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-jxc", List.of(
                buildRelease("org.glassfish.jaxb:jaxb-jxc", List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-2222", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-runtime", List.of(
                buildRelease("org.glassfish.jaxb:jaxb-runtime", List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-3333", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-xjc", List.of(
                buildRelease("org.glassfish.jaxb:jaxb-xjc", List.of("4.1.0.rhlw001"), List.of(
                    buildCve("CVE-2026-4242", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:txw2", List.of(
                buildRelease("org.glassfish.jaxb:txw2", List.of("4.1.0.rhlw001"), List.of(
                    buildCve("CVE-2026-4242", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:txwc2", List.of(
                buildRelease("org.glassfish.jaxb:txwc2", List.of("4.1.0.rhlw001"), List.of(
                    buildCve("CVE-2026-4242", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:xsom", List.of(
                buildRelease("org.glassfish.jaxb:xsom", List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-0909", "critical")
                ))
            )),
            buildPackageEvent("org.json:json", List.of(
                buildRelease("org.json:json", List.of("20220320.0.0.rhlw-00002", "20220320.0.0.rhlw-00001"), List.of(
                    buildCve("CVE-2026-0909", "critical")
                ))
            ))
        ));
        return action;
    }

    private static Event buildPackageEvent(String packageName, List<Map<String, Object>> releases) {
        return new Event.EventBuilder()
            .withMetadata(new Metadata.MetadataBuilder().build())
            .withPayload(
                new Payload.PayloadBuilder()
                    .withAdditionalProperty("package_name", packageName)
                    .withAdditionalProperty("releases", releases)
                    .build()
            )
            .build();
    }

    private static Map<String, Object> buildRelease(String packageName, List<String> releaseNames, List<Map<String, Object>> relatedCves) {
        return Map.of(
            "meta", Map.of(
                "package_name", packageName,
                "package_link", "https://console.redhat.com/lightwell/packages/" + packageName
            ),
            "release_names", releaseNames.stream().map(name -> Map.of("name", (Object) name)).toList(),
            "related_cve", relatedCves
        );
    }

    private static Map<String, Object> buildCve(String cveId, String severity) {
        return Map.of(
            "cve", cveId,
            "url", "https://console.redhat.com/api/lightwell/cves/" + cveId + ".json",
            "severity", severity
        );
    }
}
