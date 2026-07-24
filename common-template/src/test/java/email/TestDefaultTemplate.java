package email;

import com.redhat.cloud.event.parser.ConsoleCloudEventParser;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.Severity;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import email.pojo.NotificationsConsoleCloudEvent;
import helpers.BaseTransformer;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectSpy;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
public class TestDefaultTemplate extends EmailTemplatesRendererHelper {

    private static final String EVENT_TYPE_NAME = "event-type-without-template";

    @InjectSpy
    protected TemplateService templateService;

    @Inject
    BaseTransformer baseTransformerHelper;

    @Override
    protected String getApp() {
        return "test-app";
    }

    @Override
    protected String getAppDisplayName() {
        return "Test App";
    }

    final String jsonCloudEvent = "{" +
        "  \"id\":\"2de1e968-b851-47b1-a8ac-1d355ad223bb\"," +
        "  \"source\":\"urn:redhat:source:console:insights:advisor\"," +
        "  \"subject\":\"urn:redhat:subject:rhel_system:2279dc9f-bbc6-4477-b7e3-6c68d39f0d07\"," +
        "  \"time\":\"2023-05-03T02:09:06.245424792Z\"," +
        "  \"type\":\"com.redhat.console.insights.advisor.new-recommendation\"," +
        "  \"data\":{" +
        "    \"advisor_recommendations\":[" +
        "      {" +
        "        \"rule_id\":\"sample_rule|SAMPLE_RULE_ERROR_KEY\"," +
        "        \"rule_description\":\"This is a sample recommendation for testing\"," +
        "        \"total_risk\":\"2\"," +
        "        \"publish_date\":\"2023-05-03T02:09:06.245424792Z\"," +
        "        \"reboot_required\":false," +
        "        \"rule_url\":\"https://console.stage.redhat.com/insights/advisor/recommendations/sample_rule%7CSAMPLE_RULE_ERROR_KEY\"" +
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
        "  \"dataschema\":\"https://console.redhat.com/api/schemas/apps/advisor/v1/advisor-recommendations.json\"," +
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
        eventTypeDisplayName = "Test Event Type";

        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Instant notification - Test Event Type - Test App - Red Hat Enterprise Linux", result);

        // Test with Moderate severity level
        action.setSeverity(Severity.MODERATE.name());
        String moderateResult = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("[MODERATE] Instant notification - Test Event Type - Test App - Red Hat Enterprise Linux", moderateResult);

        // Test with None severity level
        action.setSeverity(Severity.NONE.name());
        String noneResult = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Instant notification - Test Event Type - Test App - Red Hat Enterprise Linux", noneResult);

        // Test with Undefined severity level
        action.setSeverity(Severity.UNDEFINED.name());
        String undefinedResult = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertEquals("Instant notification - Test Event Type - Test App - Red Hat Enterprise Linux", undefinedResult);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantEmailBody(boolean useBetaTemplate) {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        eventTypeDisplayName = "Test Event Type";
        String result = generateEmailBody(EVENT_TYPE_NAME, action, useBetaTemplate);

        assertTrue(result.contains("Red Hat Enterprise Linux/Test App/Test Event Type notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }

    /**
     * connector-email renders this same Default template but with "action" as a plain
     * {@code Map<String, Object>} (the deserialized {@code event_data} of the Kafka message it
     * consumes) rather than an {@link Action} instance, so {@code action.toPrettyJson()} must
     * also resolve against a {@code Map}.
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantEmailBodyFromMapAction(boolean useBetaTemplate) {
        Action baseAction = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        JsonObject actionJson = baseTransformerHelper.toJsonObject(baseAction);
        actionJson.put("orgId", baseAction.getOrgId());
        eventTypeDisplayName = "Test Event Type";
        TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_BODY, getBundle(), getApp(), EVENT_TYPE_NAME, useBetaTemplate);
        String result = generateEmail(templateDefinition, actionJson.getMap(), null, false);

        assertTrue(result.contains("Red Hat Enterprise Linux/Test App/Test Event Type notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
        assertTrue(result.contains("my-bundle"), "Raw action content section should render the map as pretty JSON");
        assertTrue(result.contains(TestHelpers.POLICY_ID_1), "Raw action content section should render nested map/list values");
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

        assertEquals("Instant notification - Event without template - Test App - Red Hat Enterprise Linux", result.trim());
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

        assertTrue(result.contains("Red Hat Enterprise Linux/Test App/Event without template notification was triggered."), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }
}
