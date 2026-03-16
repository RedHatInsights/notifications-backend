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

import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.RETIRING_LIFECYCLE;
import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        String result = generateEmailSubject(RETIRING_LIFECYCLE, createLifecycleAction());
        assertEquals("Instant notification - lifecycle monthly report - Planning - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testRetiringLifecycleEmailBody() {
        String result = generateEmailBody(RETIRING_LIFECYCLE, createLifecycleAction());
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        // Verify RHEL retired data is present
        assertTrue(result.contains("6"), "Body should contain rhel_versions_count");
        assertTrue(result.contains("5"), "Body should contain systems_count for retired RHEL");
        // Verify RHEL near retirement data is present
        assertTrue(result.contains("1"), "Body should contain rhel_versions_count for near retirement");
        assertTrue(result.contains("3"), "Body should contain systems_count for near retirement RHEL");
    }

    @Test
    public void testRetiringLifecycleEmailBodyContainsReportDate() {
        String result = generateEmailBody(RETIRING_LIFECYCLE, createLifecycleAction());
        assertTrue(result.contains("15th Dec 2025"), "Body should contain the report date");
    }

    @Test
    public void testRetiringLifecycleEmailBodyContainsAppstreamData() {
        String result = generateEmailBody(RETIRING_LIFECYCLE, createLifecycleAction());
        // Verify appstream retired data
        assertTrue(result.contains("2"), "Body should contain appstream retired count for rhel8");
        assertTrue(result.contains("8"), "Body should contain systems_count for rhel9");
        // Verify appstream near retirement data
        assertTrue(result.contains("22"), "Body should contain appstream near retirement count");
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithOnlyRetiredRhel() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        action.setEventType(StringUtils.EMPTY);
        action.setRecipients(List.of());

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

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        String result = generateEmailBody(RETIRING_LIFECYCLE, action);
        assertTrue(result.contains("3"), "Body should contain retired rhel_versions_count");
        assertTrue(result.contains("10"), "Body should contain retired systems_count");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithOnlyNearRetirementRhel() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        action.setEventType(StringUtils.EMPTY);
        action.setRecipients(List.of());

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
                        .withAdditionalProperty("rhel_near_retirement", Map.of("rhel_versions_count", 2, "systems_count", 7))
                        .build()
                )
                .build()
        ));

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        String result = generateEmailBody(RETIRING_LIFECYCLE, action);
        assertTrue(result.contains("2"), "Body should contain near retirement rhel_versions_count");
        assertTrue(result.contains("7"), "Body should contain near retirement systems_count");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithZeroCounts() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        action.setEventType(StringUtils.EMPTY);
        action.setRecipients(List.of());

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

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        String result = generateEmailBody(RETIRING_LIFECYCLE, action);
        // Template should still render without errors
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithAppstreamOnly() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        action.setEventType(StringUtils.EMPTY);
        action.setRecipients(List.of());

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
                        .withAdditionalProperty("appstream_retired", Map.of("rhel8", Map.of("count", 3, "systems_count", 4)))
                        .withAdditionalProperty("appstream_near_retirement", Map.of("rhel9", Map.of("count", 10, "systems_count", 12)))
                        .build()
                )
                .build()
        ));

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        String result = generateEmailBody(RETIRING_LIFECYCLE, action);
        assertTrue(result.contains("3"), "Body should contain appstream retired count");
        assertTrue(result.contains("4"), "Body should contain appstream retired systems_count");
        assertTrue(result.contains("10"), "Body should contain appstream near retirement count");
        assertTrue(result.contains("12"), "Body should contain appstream near retirement systems_count");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testRetiringLifecycleEmailBodyWithMultipleRhelVersions() {
        Action action = new Action();
        action.setBundle(StringUtils.EMPTY);
        action.setApplication(StringUtils.EMPTY);
        action.setTimestamp(LocalDateTime.of(2022, 10, 3, 15, 22, 13, 25));
        action.setEventType(StringUtils.EMPTY);
        action.setRecipients(List.of());

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

        action.setAccountId(StringUtils.EMPTY);
        action.setOrgId(DEFAULT_ORG_ID);

        String result = generateEmailBody(RETIRING_LIFECYCLE, action);
        // Verify all RHEL versions are present
        assertTrue(result.contains("5"), "Body should contain count for rhel8");
        assertTrue(result.contains("3"), "Body should contain count for rhel9");
        assertTrue(result.contains("2"), "Body should contain count for rhel10");
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
