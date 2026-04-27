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

import static com.redhat.cloud.notifications.qute.templates.mapping.Rhel.ROADMAP_REPORT;
import static helpers.TestHelpers.DEFAULT_ORG_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestRoadmapTemplate extends EmailTemplatesRendererHelper {

    public static Action createRoadmapAction() {
        Action emailActionMessage = new Action();
        emailActionMessage.setBundle(StringUtils.EMPTY);
        emailActionMessage.setApplication(StringUtils.EMPTY);
        emailActionMessage.setTimestamp(LocalDateTime.of(2026, 5, 1, 10, 30, 0, 0));
        emailActionMessage.setEventType(StringUtils.EMPTY);
        emailActionMessage.setRecipients(List.of());

        emailActionMessage.setContext(
            new Context.ContextBuilder()
                .withAdditionalProperty("roadmap", Map.of("report_date", "1st May 2026"))
                .build()
        );

        emailActionMessage.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecations", Map.of("count", 5))
                        .withAdditionalProperty("changes", Map.of("count", 3))
                        .withAdditionalProperty("additions", Map.of("count", 4))
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
        return "roadmap";
    }

    @Override
    protected String getAppDisplayName() {
        return "Roadmap";
    }

    @Test
    public void testRoadmapEmailTitle() {
        eventTypeDisplayName = "roadmap monthly report";
        String result = generateEmailSubject(ROADMAP_REPORT, createRoadmapAction());
        assertEquals("Instant notification - roadmap monthly report - Roadmap - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testRoadmapEmailBody() {
        String result = generateEmailBody(ROADMAP_REPORT, createRoadmapAction());
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        assertTrue(result.contains("id=\"deprecationsCount\">5<"), "Body should contain deprecations count");
        assertTrue(result.contains("id=\"changesCount\">3<"), "Body should contain changes count");
        assertTrue(result.contains("id=\"additionsCount\">4<"), "Body should contain additions count");
    }

    @Test
    public void testRoadmapEmailBodyContainsReportDate() {
        String result = generateEmailBody(ROADMAP_REPORT, createRoadmapAction());
        assertTrue(result.contains("1st May 2026"), "Body should contain the report date");
    }

    @Test
    public void testRoadmapEmailBodyWithAllZeroCounts() {
        Action action = createRoadmapAction();

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecations", Map.of("count", 0))
                        .withAdditionalProperty("changes", Map.of("count", 0))
                        .withAdditionalProperty("additions", Map.of("count", 0))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(ROADMAP_REPORT, action);
        assertTrue(result.contains("Your inventory remained fully supported"), "Body should contain 'Your inventory remained fully supported' when all counts are 0");
        assertTrue(result.contains("A value of 0 indicates that no roadmap changes affected your systems this month"), "Body should contain explanation for all zeros");
        assertTrue(result.contains("id=\"deprecationsCount\">0<"), "Body should contain deprecations count 0");
        assertTrue(result.contains("id=\"changesCount\">0<"), "Body should contain changes count 0");
        assertTrue(result.contains("id=\"additionsCount\">0<"), "Body should contain additions count 0");
    }

    @Test
    public void testRoadmapEmailBodyWithSomeZeroCounts() {
        Action action = createRoadmapAction();

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecations", Map.of("count", 5))
                        .withAdditionalProperty("changes", Map.of("count", 0))
                        .withAdditionalProperty("additions", Map.of("count", 2))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(ROADMAP_REPORT, action);
        assertTrue(result.contains("Certain roadmap changes affected your systems"), "Body should contain 'Certain roadmap changes affected your systems' when some counts are 0");
        assertTrue(result.contains("A value of 0 in any category indicated that no additional changes affected your inventory"), "Body should contain explanation for some zeros");
        assertTrue(result.contains("id=\"deprecationsCount\">5<"), "Body should contain deprecations count");
        assertTrue(result.contains("id=\"changesCount\">0<"), "Body should contain changes count 0");
        assertTrue(result.contains("id=\"additionsCount\">2<"), "Body should contain additions count");
    }

    @Test
    public void testRoadmapEmailBodyWithOnlyDeprecations() {
        Action action = createRoadmapAction();

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecations", Map.of("count", 7))
                        .withAdditionalProperty("changes", Map.of("count", 0))
                        .withAdditionalProperty("additions", Map.of("count", 0))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(ROADMAP_REPORT, action);
        assertTrue(result.contains("id=\"deprecationsCount\">7<"), "Body should contain deprecations count");
        assertTrue(result.contains("id=\"changesCount\">0<"), "Body should contain changes count 0");
        assertTrue(result.contains("id=\"additionsCount\">0<"), "Body should contain additions count 0");
        assertTrue(result.contains("Certain roadmap changes affected your systems"), "Body should contain partial zero message");
    }

    @Test
    public void testRoadmapEmailBodyWithOnlyChanges() {
        Action action = createRoadmapAction();

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecations", Map.of("count", 0))
                        .withAdditionalProperty("changes", Map.of("count", 8))
                        .withAdditionalProperty("additions", Map.of("count", 0))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(ROADMAP_REPORT, action);
        assertTrue(result.contains("id=\"deprecationsCount\">0<"), "Body should contain deprecations count 0");
        assertTrue(result.contains("id=\"changesCount\">8<"), "Body should contain changes count");
        assertTrue(result.contains("id=\"additionsCount\">0<"), "Body should contain additions count 0");
        assertTrue(result.contains("Certain roadmap changes affected your systems"), "Body should contain partial zero message");
    }

    @Test
    public void testRoadmapEmailBodyWithOnlyAdditions() {
        Action action = createRoadmapAction();

        action.setEvents(List.of(
            new Event.EventBuilder()
                .withMetadata(new Metadata.MetadataBuilder().build())
                .withPayload(
                    new Payload.PayloadBuilder()
                        .withAdditionalProperty("deprecations", Map.of("count", 0))
                        .withAdditionalProperty("changes", Map.of("count", 0))
                        .withAdditionalProperty("additions", Map.of("count", 10))
                        .build()
                )
                .build()
        ));

        String result = generateEmailBody(ROADMAP_REPORT, action);
        assertTrue(result.contains("id=\"deprecationsCount\">0<"), "Body should contain deprecations count 0");
        assertTrue(result.contains("id=\"changesCount\">0<"), "Body should contain changes count 0");
        assertTrue(result.contains("id=\"additionsCount\">10<"), "Body should contain additions count");
        assertTrue(result.contains("Certain roadmap changes affected your systems"), "Body should contain partial zero message");
    }

    @Test
    public void testRoadmapEmailBodyContainsAllSectionDescriptions() {
        String result = generateEmailBody(ROADMAP_REPORT, createRoadmapAction());
        assertTrue(result.contains("Deprecations that could affect your systems"), "Body should contain deprecations description");
        assertTrue(result.contains("Changes that could affect your systems"), "Body should contain changes description");
        assertTrue(result.contains("Additions &amp; enhancements that could affect your systems"), "Body should contain additions description");
    }
}
