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
public class TestPlanningTemplate extends EmailTemplatesRendererHelper {

    public static Action createPlanningAction() {
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
        return "planning";
    }

    @Override
    protected String getAppDisplayName() {
        return "Planning";
    }

    @Test
    public void testRetiringLifecycleEmailTitle() {
        eventTypeDisplayName = "Lifecycle Biweekly report";
        String result = generateEmailSubject(RETIRING_LIFECYCLE, createPlanningAction());
        assertEquals("Instant notification - Lifecycle Biweekly report - Planning - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testRetiringLifecycleEmailBody() {
        String result = generateEmailBody(RETIRING_LIFECYCLE, createPlanningAction());
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
