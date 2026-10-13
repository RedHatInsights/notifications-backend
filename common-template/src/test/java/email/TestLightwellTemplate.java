package email;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.Severity;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.mapping.Lightwell.LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE;
import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static helpers.TestHelpers.HCC_LOGO_TARGET;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    public static final String LIGHTWELL_LOGO = "Logo_lightwell_dark.png";

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
    }

    @Test
    public void testJavaRemediatedEmailTitle() {
        Action action = createLightwellAction();
        eventTypeDisplayName = "Java Remediated";

        String result = generateEmailSubject(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action);
        assertEquals("Instant notification - Java Remediated - Lightwell", result);

        action.setSeverity(Severity.CRITICAL.name());
        String criticalResult = generateEmailSubject(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action);
        assertEquals("[CRITICAL] Instant notification - Java Remediated - Lightwell", criticalResult);

        action.setSeverity(Severity.NONE.name());
        String noneResult = generateEmailSubject(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action);
        assertEquals("Instant notification - Java Remediated - Lightwell", noneResult);

        action.setSeverity(Severity.UNDEFINED.name());
        String undefinedResult = generateEmailSubject(LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE, action);
        assertEquals("Instant notification - Java Remediated - Lightwell", undefinedResult);
    }

    @Test
    public void testJavaRemediatedEmailBodySingleEvent() {
        Action action = createLightwellActionWithReleases(List.of(
            buildRelease(List.of("4.0.4.rhlw003"), List.of(
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
                buildRelease(List.of("4.0.4.rhlw003", "4.0.4.rhlw004"), List.of(
                    buildCve("CVE-2026-1234", "critical"),
                    buildCve("CVE-2026-5678", "critical"),
                    buildCve("CVE-2026-9999", "critical")
                )),
                buildRelease(List.of("5.0.0.rhlw001"), List.of(
                    buildCve("CVE-2026-1234", "critical"),
                    buildCve("CVE-2026-5678", "critical"),
                    buildCve("CVE-2026-9999", "critical")
                )),
                buildRelease(List.of("5.5.5.rhlw001"), List.of(
                    buildCve("CVE-2026-1234", "critical"),
                    buildCve("CVE-2026-5678", "critical"),
                    buildCve("CVE-2026-9999", "critical")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-core", List.of(
                buildRelease(List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-1111", "important"),
                    buildCve("CVE-2026-2222", "important"),
                    buildCve("CVE-2026-9999", "critical")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-jxc", List.of(
                buildRelease(List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-2222", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-runtime", List.of(
                buildRelease(List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-3333", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:jaxb-xjc", List.of(
                buildRelease(List.of("4.1.0.rhlw001"), List.of(
                    buildCve("CVE-2026-4242", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:txw2", List.of(
                buildRelease(List.of("4.1.0.rhlw001"), List.of(
                    buildCve("CVE-2026-4242", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:txwc2", List.of(
                buildRelease(List.of("4.1.0.rhlw001"), List.of(
                    buildCve("CVE-2026-4242", "important")
                ))
            )),
            buildPackageEvent("org.glassfish.jaxb:xsom", List.of(
                buildRelease(List.of("4.0.4.rhlw003"), List.of(
                    buildCve("CVE-2026-0909", "critical")
                ))
            )),
            buildPackageEvent("org.json:json", List.of(
                buildRelease(List.of("20220320.0.0.rhlw-00002", "20220320.0.0.rhlw-00001"), List.of(
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
                    .withAdditionalProperty("package_link", "https://console.redhat.com/lightwell/packages/" + packageName)
                    .withAdditionalProperty("releases", releases)
                    .build()
            )
            .build();
    }

    private static Map<String, Object> buildRelease(List<String> releaseNames, List<Map<String, Object>> relatedCves) {
        return Map.of(
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

    @Test
    public void testWeeklyEmailBodyMultipleEventTypes() throws JsonProcessingException {
        Map<String, Object> context = createWeeklyDigestContext(
            Map.of(
                "java-remediated", createEventTypePackages("Java Remediated", List.of(
                    createPackageInfo("org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel", 3),
                    createPackageInfo("org.glassfish.jaxb:jaxb-core", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core", 1)
                )),
                "python-remediated", createEventTypePackages("Python Remediated", List.of(
                    createPackageInfo("requests", "https://console.redhat.com/lightwell/packages/requests", 2)
                ))
            ),
            5, 3
        );

        String result = generateAggregatedEmailBody(context, null, IntegrationType.EMAIL_WEEKLY_DIGEST_BODY, false);

        // Bundle-level aggregation shows HCC logo
        assertTrue(result.contains(HCC_LOGO_TARGET));

        // Coverage Updates summary section
        assertTrue(result.contains("Coverage Updates"));
        assertTrue(result.contains("Repositories"));
        assertTrue(result.contains("Packages"));

        // Event type sections with titles
        assertTrue(result.contains("New Packages Available for Java Remediated"));
        assertTrue(result.contains("New Packages Available for Python Remediated"));

        // Package links
        assertTrue(result.contains("org.glassfish.jaxb:codemodel"));
        assertTrue(result.contains("https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel"));
        assertTrue(result.contains("org.glassfish.jaxb:jaxb-core"));
        assertTrue(result.contains("requests"));

        // Intro text
        assertTrue(result.contains("The following packages were fixed by Lightwell"));

        // CTA button
        assertTrue(result.contains("Go to Lightwell"));
        assertTrue(result.contains("from=notifications&integration=weekly_digest"));
    }

    @Test
    public void testWeeklyEmailBodySingleEventType() throws JsonProcessingException {
        Map<String, Object> context = createWeeklyDigestContext(
            Map.of(
                "java-remediated", createEventTypePackages("Java Remediated", List.of(
                    createPackageInfo("org.json:json", "https://console.redhat.com/lightwell/packages/org.json:json", 1)
                ))
            ),
            1, 1
        );

        String result = generateAggregatedEmailBody(context, null, IntegrationType.EMAIL_WEEKLY_DIGEST_BODY, false);

        // Singular forms in summary
        assertTrue(result.contains("Repository"));
        assertFalse(result.contains("Repositories"));
        assertTrue(result.contains("Package"));

        assertTrue(result.contains("New Packages Available for Java Remediated"));
        assertTrue(result.contains("org.json:json"));
    }

    @Test
    public void testWeeklyEmailBodyMultiplePackagesSingleEventType() throws JsonProcessingException {
        Map<String, Object> context = createWeeklyDigestContext(
            Map.of(
                "java-remediated", createEventTypePackages("Java Remediated", List.of(
                    createPackageInfo("org.glassfish.jaxb:codemodel", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel", 3),
                    createPackageInfo("org.glassfish.jaxb:jaxb-core", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core", 1),
                    createPackageInfo("org.glassfish.jaxb:txw2", "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:txw2", 2)
                ))
            ),
            6, 3
        );

        String result = generateAggregatedEmailBody(context, null, IntegrationType.EMAIL_WEEKLY_DIGEST_BODY, false);

        assertTrue(result.contains("Coverage Updates"));
        assertTrue(result.contains("Repository"));
        assertTrue(result.contains("Packages"));

        assertTrue(result.contains("org.glassfish.jaxb:codemodel"));
        assertTrue(result.contains("org.glassfish.jaxb:jaxb-core"));
        assertTrue(result.contains("org.glassfish.jaxb:txw2"));
    }

    private Map<String, Object> createWeeklyDigestContext(Map<String, Map<String, Object>> packagesByEventType, int totalAdvisories, int totalPackages) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("total_advisories", totalAdvisories);
        context.put("total_packages", totalPackages);
        context.put("total_repositories", packagesByEventType.size());
        context.put("packages_by_event_type", packagesByEventType);
        context.put("application", "lightwell");
        return context;
    }

    private Map<String, Object> createEventTypePackages(String eventTypeDisplayName, List<Map<String, Object>> packages) {
        Map<String, Object> eventTypeData = new LinkedHashMap<>();
        eventTypeData.put("event_type_display_name", eventTypeDisplayName);
        eventTypeData.put("packages", packages);
        return eventTypeData;
    }

    private Map<String, Object> createPackageInfo(String packageName, String packageLink, int advisoryCount) {
        Map<String, Object> packageInfo = new LinkedHashMap<>();
        packageInfo.put("package_name", packageName);
        packageInfo.put("package_link", packageLink);
        packageInfo.put("advisory_count", advisoryCount);
        return packageInfo;
    }
}
