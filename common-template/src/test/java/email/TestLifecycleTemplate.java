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
        assertEquals("Instant notification - life cycle monthly report - Life Cycle - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testRetiringLifecycleEmailBody() {
        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, createLifecycleAction());
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        // Verify RHEL retired data is present
        assertTrue(result.contains("id=\"rhelRetiredCount\">6<"), "Body should contain rhel_versions_count for retired RHEL");
        // Verify RHEL near retirement data is present
        assertTrue(result.contains("id=\"rhelExpiringCount\">1<"), "Body should contain rhel_versions_count for near retirement RHEL");
    }

    @Test
    public void testRetiringLifecycleEmailBodyContainsReportDate() {
        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, createLifecycleAction());
        assertTrue(result.contains("15th Dec 2025"), "Body should contain the report date");
    }

    @Test
    public void testRetiringLifecycleEmailBodyContainsAppstreamData() {
        String result = generateEmailBody(RETIRING_LIFECYCLE_REPORT, createLifecycleAction());
        // Verify appstream retired data
        assertTrue(result.contains("id=\"rhel8RetiredCount\">2<"), "Body should contain appstream retired count for rhel8");
        assertTrue(result.contains("id=\"rhel9RetiredCount\">2<"), "Body should contain appstream retired count for rhel9");
        // Verify appstream near retirement data
        assertTrue(result.contains("id=\"rhel8ExpiringCount\">5<"), "Body should contain appstream near retirement count for rhel8");
        assertTrue(result.contains("id=\"rhel9ExpiringCount\">22<"), "Body should contain appstream near retirement count for rhel9");
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
        assertFalse(result.contains("application streams life cycle"), "Body shouldn't contain any appstreams section");
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
        assertFalse(result.contains("application streams life cycle"), "Body shouldn't contain any appstreams section");
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
        assertFalse(result.contains("RHEL life cycle"), "Body shouldn't contain 'RHEL life cycle' section");
        assertFalse(result.contains("application streams life cycle"), "Body shouldn't contain any appstreams section");
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
        assertTrue(result.contains("id=\"rhel8RetiredCount\">3<"), "Body should contain appstream retired count for rhel8");
        assertTrue(result.contains("id=\"rhel8ExpiringCount\">0<"), "Body should contain near retirement count for rhel8");
        assertTrue(result.contains("id=\"rhel9RetiredCount\">0<"), "Body should contain appstream retired count for rhel9");
        assertTrue(result.contains("id=\"rhel9ExpiringCount\">10<"), "Body should contain appstream near retirement count for rhel9");
        assertFalse(result.contains("RHEL life cycle"), "Body shouldn't contain 'RHEL life cycle' section");
        assertFalse(result.contains("RHEL 10 application streams life cycle"), "Body shouldn't contain RHEL 10 appstreams section");
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
        // Verify all RHEL versions are present
        assertTrue(result.contains("id=\"rhel8RetiredCount\">5<"), "Body should contain appstream retired count for rhel8");
        assertTrue(result.contains("id=\"rhel9RetiredCount\">3<"), "Body should contain appstream retired count for rhel9");
        assertTrue(result.contains("id=\"rhel10RetiredCount\">2<"), "Body should contain appstream retired count for rhel10");
        assertTrue(result.contains("id=\"rhel8ExpiringCount\">0<"), "Body should contain near retirement count for rhel8");
        assertTrue(result.contains("id=\"rhel9ExpiringCount\">0<"), "Body should contain near retirement count for rhel9");
        assertTrue(result.contains("id=\"rhel10ExpiringCount\">0<"), "Body should contain near retirement count for rhel10");
        assertFalse(result.contains("RHEL life cycle"), "Body shouldn't contain 'RHEL life cycle' section");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodySectionVisibilityBasedOnSystemsCount() {
        // Test that sections are shown based on systems_count, not retired/expiring counts
        Action action = createLifecycleAction();

        action.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("lifecycle", Map.of("report_date", "1st Jan 2026"))
                .build()
        );

        // RHEL section with counts but no systems should not be displayed
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
        assertFalse(result.contains("RHEL life cycle"), "Body shouldn't contain 'RHEL life cycle' section when systems_count is 0");
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
