package email;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import email.pojo.NotificationsConsoleCloudEvent;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class TestDefaultTemplate extends EmailTemplatesRendererHelper {

    private static final String EVENT_TYPE_NAME = "event-type-without-template";

    @InjectSpy
    protected TemplateService templateService;

    @Override
    protected String getApp() {
        return "policies";
    }

    @Override
    protected String getAppDisplayName() {
        return "Policies";
    }

    final String jsonCloudEvent = "{" +
        "  \"id\":\"2de1e968-b851-47b1-a8ac-1d355ad223bb\"," +
        "  \"source\":\"urn:redhat:source:policies:insights:policies\"," +
        "  \"subject\":\"urn:redhat:subject:rhel_system:2279dc9f-bbc6-4477-b7e3-6c68d39f0d07\"," +
        "  \"time\":\"2023-05-03T02:09:06.245424792Z\"," +
        "  \"type\":\"com.redhat.console.insights.policies.policy-triggered\"," +
        "  \"data\":{" +
        "    \"policies\":[" +
        "      {" +
        "        \"condition\":\"facts.arch = \\\"x86_64\\\"\"," +
        "        \"description\":\"This is a sample policy for testing\"," +
        "        \"id\":\"d41049d9-23e0-47ae-bd27-ecd1615fd200\"," +
        "        \"name\":\"iqe-policies-2023-02-20-00:03:36:664678\"," +
        "        \"url\":\"https://console.stage.redhat.com//insights/policies/policy/d41049d9-23e0-47ae-bd27-ecd1615fd200\"" +
        "      }" +
        "    ]," +
        "    \"system\":{" +
        "      \"check_in\":\"2023-05-03T02:09:05.828152Z\"," +
        "      \"display_name\":\"iqe-patch-rhel-80-tag-a66a9f1f-6ffa-4925-815e-855467f70cec\"," +
        "      \"tags\":[" +
        "        {" +
        "          \"key\":\"patch_1fi0\"," +
        "          \"namespace\":\"insights-client\"," +
        "          \"value\":\"patchman-ui\"" +
        "        }" +
        "      ]," +
        "      \"inventory_id\":\"2279dc9f-bbc6-4477-b7e3-6c68d39f0d07\"" +
        "    }" +
        "  }," +
        "  \"$schema\":\"https://console.redhat.com/api/schemas/events/v1/events.json\"," +
        "  \"specversion\":\"1.0\"," +
        "  \"dataschema\":\"https://console.redhat.com/api/schemas/apps/policies/v1/policy-triggered.json\"," +
        "  \"redhatorgid\":\"11789772\"," +
        "  \"redhataccount\":\"6089719\"" +
        "}";

    @BeforeEach
    void beforeEach() {
        when(templateService.isDefaultEmailTemplateEnabled()).thenReturn(true);
        templateService.init();
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        eventTypeDisplayName = "Policy Triggered";

        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Instant notification - Policy Triggered - Policies - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        eventTypeDisplayName = "Policy Triggered";
        String result = generateEmailBody(EVENT_TYPE_NAME, action);

        assertTrue(result.contains("Red Hat Enterprise Linux/Policies/Policy Triggered notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }


    @Test
    public void testInstantEmailTitleCloudEvents() {
        NotificationsConsoleCloudEvent event = new ConsoleCloudEventParser().fromJsonString(
            jsonCloudEvent,
            NotificationsConsoleCloudEvent.class
        );

        eventTypeDisplayName = "Event without template";
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_TITLE, getBundle(), getApp(), EVENT_TYPE_NAME);
        String result = generateEmail(templateDefinition, event, null, false);

        assertEquals("Instant notification - Event without template - Policies - Red Hat Enterprise Linux", result.trim());
    }

    @Test
    public void testInstantEmailBodyCloudEvents() {
        NotificationsConsoleCloudEvent event = new ConsoleCloudEventParser().fromJsonString(
            jsonCloudEvent,
            NotificationsConsoleCloudEvent.class
        );

        eventTypeDisplayName = "Event without template";
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_BODY, getBundle(), getApp(), EVENT_TYPE_NAME);
        String result = generateEmail(templateDefinition, event, null, false);

        assertTrue(result.contains("Red Hat Enterprise Linux/Policies/Event without template notification was triggered."), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }
}
