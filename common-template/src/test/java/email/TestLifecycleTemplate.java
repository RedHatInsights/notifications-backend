package email;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.RETIRING_LIFECYCLE_REPORT;
import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestLifecycleTemplate extends EmailTemplatesRendererHelper {

    public static Action createLifecycleAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        emailActionMessage.setEventType(StringUtils.EMPTY);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "15th Dec 2025"))
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 6, "systems_count", 5))
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 1, "systems_count", 3))
                        .withAdditionalProperty("appstream_retired", Map.of("rhel8", Map.of("count", 2, "systems_count", 5), "rhel9", Map.of("count", 2, "systems_count", 8), "rhel10", Map.of("count", 1, "systems_count", 1)))
                        .withAdditionalProperty("appstream_near_retirement", Map.of("rhel8", Map.of("count", 5, "systems_count", 7), "rhel9", Map.of("count", 22, "systems_count", 6), "rhel10", Map.of("count", 1, "systems_count", 1)))
                        .build()
                )
                .build()
        ));

        emailActionMessage.setAccountId(StringUtils.EMPTY);
        emailActionMessage.setOrgId(DEFAULT_ORG_ID);

        return emailActionMessage;
    }

    @Override
    protected String getApp() {
        return "life-cycle";
    }

    @Override
    protected String getAppDisplayName() {
        return "Life Cycle";
    }

    @Test
    public void testRetiringLifecycleEmailTitle() {
        eventTypeDisplayName = "life cycle monthly report";
        String result = generateEmailSubject(RETIRING_LIFECYCLE_REPORT, createLifecycleAction());
        assertEquals("Monthly report - Life Cycle - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testRetiringLifecycleEmailBody() {
        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, createLifecycleAction());
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        // Verify RHEL retired data is present
        assertTrue(result.contains("id=\"rhelRetiredCount\">6<"), "Body should contain rhel_versions_count for retired RHEL");
        // Verify RHEL near retirement data is present
        assertTrue(result.contains("id=\"rhelExpiringCount\">1<"), "Body should contain rhel_versions_count for near retirement RHEL");
        // Verify lifecycle URLs are properly resolved with the environment base URL
        assertTrue(result.contains("href=\"https://localhost/insights/planning?lifecycleDropdown=Red+Hat+Enterprise+Linux"), "Retired RHEL link should contain resolved environment URL");
        assertTrue(result.contains("href=\"https://localhost/insights/planning?lifecycleDropdown=Red+Hat+Enterprise+Linux"), "Expiring RHEL link should contain resolved environment URL");
        assertFalse(result.contains("href=\"{environment.url}"), "Links should not contain unresolved template expression");
    }

    @Test
    public void testRetiringLifecycleEmailBodyContainsReportDate() {
        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, createLifecycleAction());
        assertTrue(result.contains("15th Dec 2025"), "Body should contain the report date");
    }

    @Test
    public void testRetiringLifecycleEmailBodyContainsAppstreamData() {
        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, createLifecycleAction());
        // Verify appstream sections appear when payload has the data
        assertTrue(result.contains("RHEL 8 Application Streams Life Cycle"), "Body should contain RHEL 8 appstreams section");
        assertTrue(result.contains("RHEL 9 Application Streams Life Cycle"), "Body should contain RHEL 9 appstreams section");
        assertTrue(result.contains("RHEL 10 Application Streams Life Cycle"), "Body should contain RHEL 10 appstreams section");
        // Verify appstream count IDs are present
        assertTrue(result.contains("id=\"rhel8RetiredCount\""), "Body should contain rhel8 retired count ID");
        assertTrue(result.contains("id=\"rhel9RetiredCount\""), "Body should contain rhel9 retired count ID");
        assertTrue(result.contains("id=\"rhel10RetiredCount\""), "Body should contain rhel10 retired count ID");
        assertTrue(result.contains("id=\"rhel8ExpiringCount\""), "Body should contain rhel8 expiring count ID");
        assertTrue(result.contains("id=\"rhel9ExpiringCount\""), "Body should contain rhel9 expiring count ID");
        assertTrue(result.contains("id=\"rhel10ExpiringCount\""), "Body should contain rhel10 expiring count ID");
        // Verify appstream lifecycle URLs are properly resolved
        assertTrue(result.contains("href=\"https://localhost/insights/planning?lifecycleDropdown=RHEL+8+Application+Streams"), "RHEL 8 appstream link should contain resolved environment URL");
        assertTrue(result.contains("href=\"https://localhost/insights/planning?lifecycleDropdown=RHEL+9+Application+Streams"), "RHEL 9 appstream link should contain resolved environment URL");
        assertTrue(result.contains("href=\"https://localhost/insights/planning?lifecycleDropdown=RHEL+10+Application+Streams"), "RHEL 10 appstream link should contain resolved environment URL");
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithOnlyRetiredRhel() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 3, "systems_count", 10))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        assertTrue(result.contains("id=\"rhelRetiredCount\">3<"), "Body should contain retired rhel_versions_count");
        assertTrue(result.contains("id=\"rhelExpiringCount\">0<"), "Body should contain near retirement rhel_versions_count");
        assertFalse(result.contains("Application Streams Life Cycle"), "Body shouldn't contain appstream sections since no appstream payload");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithOnlyNearRetirementRhel() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 0, "systems_count", 7))
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 2, "systems_count", 7))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        assertTrue(result.contains("id=\"rhelRetiredCount\">0<"), "Body should contain retired rhel_versions_count");
        assertTrue(result.contains("id=\"rhelExpiringCount\">2<"), "Body should contain near retirement rhel_versions_count");
        assertFalse(result.contains("Application Streams Life Cycle"), "Body shouldn't contain appstream sections since no appstream payload");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithZeroCounts() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 0, "systems_count", 0))
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 0, "systems_count", 0))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        assertTrue(result.contains("RHEL Life Cycle"), "Body should contain 'RHEL Life Cycle' section since payload exists");
        assertTrue(result.contains("id=\"rhelRetiredCount\">0<"), "Body should contain retired rhel_versions_count of 0");
        assertTrue(result.contains("id=\"rhelExpiringCount\">0<"), "Body should contain near retirement rhel_versions_count of 0");
        assertFalse(result.contains("Application Streams Life Cycle"), "Body shouldn't contain appstream sections since no appstream payload");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithAppstreamOnly() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("appstream_retired", Map.of("rhel8", Map.of("count", 3, "systems_count", 4), "rhel9", Map.of("count", 0, "systems_count", 12)))
                        .withAdditionalProperty("appstream_near_retirement", Map.of("rhel8", Map.of("count", 0, "systems_count", 4), "rhel9", Map.of("count", 10, "systems_count", 12)))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        // Verify RHEL 8 and 9 sections appear since they're in the payload
        assertTrue(result.contains("RHEL 8 Application Streams Life Cycle"), "Body should contain RHEL 8 appstreams section");
        assertTrue(result.contains("RHEL 9 Application Streams Life Cycle"), "Body should contain RHEL 9 appstreams section");
        assertTrue(result.contains("id=\"rhel8RetiredCount\""), "Body should contain rhel8 retired count ID");
        assertTrue(result.contains("id=\"rhel8ExpiringCount\""), "Body should contain rhel8 expiring count ID");
        assertTrue(result.contains("id=\"rhel9RetiredCount\""), "Body should contain rhel9 retired count ID");
        assertTrue(result.contains("id=\"rhel9ExpiringCount\""), "Body should contain rhel9 expiring count ID");
        assertFalse(result.contains("RHEL Life Cycle"), "Body shouldn't contain 'RHEL Life Cycle' section since no rhel_retired payload");
        assertFalse(result.contains("RHEL 10 Application Streams Life Cycle"), "Body shouldn't contain RHEL 10 appstreams section since no rhel10 payload");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithMultipleRhelVersions() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("appstream_retired", Map.of(
                            "rhel8", Map.of("count", 5, "systems_count", 10),
                            "rhel9", Map.of("count", 3, "systems_count", 7),
                            "rhel10", Map.of("count", 2, "systems_count", 4)
                        ))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        // Verify all RHEL appstream versions sections appear
        assertTrue(result.contains("RHEL 8 Application Streams Life Cycle"), "Body should contain RHEL 8 appstreams section");
        assertTrue(result.contains("RHEL 9 Application Streams Life Cycle"), "Body should contain RHEL 9 appstreams section");
        assertTrue(result.contains("RHEL 10 Application Streams Life Cycle"), "Body should contain RHEL 10 appstreams section");
        assertTrue(result.contains("id=\"rhel8RetiredCount\""), "Body should contain rhel8 retired count ID");
        assertTrue(result.contains("id=\"rhel9RetiredCount\""), "Body should contain rhel9 retired count ID");
        assertTrue(result.contains("id=\"rhel10RetiredCount\""), "Body should contain rhel10 retired count ID");
        assertTrue(result.contains("id=\"rhel8ExpiringCount\""), "Body should contain rhel8 expiring count ID");
        assertTrue(result.contains("id=\"rhel9ExpiringCount\""), "Body should contain rhel9 expiring count ID");
        assertTrue(result.contains("id=\"rhel10ExpiringCount\""), "Body should contain rhel10 expiring count ID");
        assertFalse(result.contains("RHEL Life Cycle"), "Body shouldn't contain 'RHEL Life Cycle' section since no rhel_retired payload");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodySectionVisibilityBasedOnPayloadExistence() {
        // Test that sections are shown based on payload existence, not systems_count values
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        // RHEL section with counts but systems_count is 0 - should still be displayed since payload exists
        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 5, "systems_count", 0))
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 3, "systems_count", 0))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        assertTrue(result.contains("RHEL Life Cycle"), "Body should contain 'RHEL Life Cycle' section since rhel_retired payload exists");
        assertTrue(result.contains("id=\"rhelRetiredCount\">5<"), "Body should contain retired rhel_versions_count");
        assertTrue(result.contains("id=\"rhelExpiringCount\">3<"), "Body should contain near retirement rhel_versions_count");
        assertFalse(result.contains("Application Streams Life Cycle"), "Body shouldn't contain appstream sections since no appstream payload");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithAllZeroCountsShowsFullySupportedMessage() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 0, "systems_count", 0))
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 0, "systems_count", 0))
                        .withAdditionalProperty("appstream_retired", Map.of(
                            "rhel8", Map.of("count", 0, "systems_count", 0),
                            "rhel9", Map.of("count", 0, "systems_count", 0),
                            "rhel10", Map.of("count", 0, "systems_count", 0)
                        ))
                        .withAdditionalProperty("appstream_near_retirement", Map.of(
                            "rhel8", Map.of("count", 0, "systems_count", 0),
                            "rhel9", Map.of("count", 0, "systems_count", 0),
                            "rhel10", Map.of("count", 0, "systems_count", 0)
                        ))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        assertTrue(result.contains("Your inventory remained fully supported"), "Body should contain fully supported message when all counts are 0");
        assertTrue(result.contains("A value of 0 indicated that no systems reached a retirement threshold or approached end-of-life dates"), "Body should contain full explanation message");
        assertFalse(result.contains("Certain systems reached a retirement threshold"), "Body should not contain partial zero message when all are 0");
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithSomeZeroCountsShowsPartialMessage() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 5, "systems_count", 10))
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 0, "systems_count", 10))
                        .withAdditionalProperty("appstream_retired", Map.of("rhel8", Map.of("count", 2, "systems_count", 5)))
                        .withAdditionalProperty("appstream_near_retirement", Map.of("rhel8", Map.of("count", 0, "systems_count", 5)))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        assertTrue(result.contains("Certain systems reached a retirement threshold"), "Body should contain partial message when some counts are 0");
        assertTrue(result.contains("A value of 0 in any category confirmed that no additional systems were affected by that specific lifecycle event"), "Body should contain partial explanation message");
        assertFalse(result.contains("Your inventory remained fully supported"), "Body should not contain fully supported message when some counts are not 0");
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithNoZeroCountsShowsNoMessage() {
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("rhel_retired", Map.of("rhel_versions_count", 5, "systems_count", 10))
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 3, "systems_count", 10))
                        .withAdditionalProperty("appstream_retired", Map.of("rhel8", Map.of("count", 2, "systems_count", 5)))
                        .withAdditionalProperty("appstream_near_retirement", Map.of("rhel8", Map.of("count", 4, "systems_count", 5)))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, action);
        assertFalse(result.contains("Your inventory remained fully supported"), "Body should not contain fully supported message when no counts are 0");
        assertFalse(result.contains("Certain systems reached a retirement threshold"), "Body should not contain partial message when no counts are 0");
    }
}
