package ms_teams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.qute.templates.Severity;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.qute.templates.mapping.OpenShift;
import helpers.OcmTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.stream.Stream;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.MS_TEAMS;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_APP_NAME;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CAPACITY_MANAGEMENT;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_ACCESS;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_ADD_ON;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_CONFIGURATION;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_LIFECYCLE;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_NETWORKING;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_OWNERSHIP;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_SCALING;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_SECURITY;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CLUSTER_UPDATE;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_CUSTOMER_SUPPORT;
import static com.redhat.cloud.notifications.qute.templates.mapping.OpenShift.CLUSTER_MANAGER_GENERAL_NOTIFICATION;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestOcmTemplate {

    private static final JsonObject MESSAGE = OcmTestHelpers.createOcmMessage("Atlantic", "OSDTrial", "<b>Altlantic</b> server is experiencing flooding issues", "Subject line!", Severity.NONE.name());

    private static final String CLUSTER_MANAGER_DEFAULT_EVENT_URL = "https://cloud.redhat.com/openshift/details/s/2XqNHRdLNEAzshh7MkkOql6fx6I?from=notifications&integration=teams";
    public static final String EXPECTED_NOTIFICATION_TEXT_MESSAGE = "\"text\": \"1 event triggered from Cluster Manager - OpenShift. [Open Cluster Manager](" + CLUSTER_MANAGER_DEFAULT_EVENT_URL + ")\"";

    @Inject
    TemplateService templateService;

    @Inject
    ObjectMapper objectMapper;

    private static Stream<Arguments> eventTypesAndBeta() {
        return Stream.of(
                Arguments.of(CLUSTER_MANAGER_CLUSTER_UPDATE, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_UPDATE, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_LIFECYCLE, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_LIFECYCLE, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_CONFIGURATION, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_CONFIGURATION, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_OWNERSHIP, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_OWNERSHIP, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_ACCESS, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_ACCESS, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_SCALING, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_SCALING, false),
                Arguments.of(CLUSTER_MANAGER_CAPACITY_MANAGEMENT, true),
                Arguments.of(CLUSTER_MANAGER_CAPACITY_MANAGEMENT, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_SECURITY, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_SECURITY, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_ADD_ON, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_ADD_ON, false),
                Arguments.of(CLUSTER_MANAGER_CUSTOMER_SUPPORT, true),
                Arguments.of(CLUSTER_MANAGER_CUSTOMER_SUPPORT, false),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_NETWORKING, true),
                Arguments.of(CLUSTER_MANAGER_CLUSTER_NETWORKING, false),
                Arguments.of(CLUSTER_MANAGER_GENERAL_NOTIFICATION, true),
                Arguments.of(CLUSTER_MANAGER_GENERAL_NOTIFICATION, false)
        );
    }

    @ParameterizedTest
    @MethodSource("eventTypesAndBeta")
    void testRenderedOcmTemplates(final String eventType, final boolean useBetaTemplate) {
        String result = renderTemplate(eventType, useBetaTemplate);
        checkResult(result, useBetaTemplate);
    }

    String renderTemplate(final String eventType, final boolean useBetaTemplate) {
        TemplateDefinition templateConfig = new TemplateDefinition(MS_TEAMS, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, eventType, useBetaTemplate);
        Map<String, Object> messageAsMap;
        try {
            messageAsMap = objectMapper.readValue(MESSAGE.encode(), Map.class);
            return templateService.renderTemplate(templateConfig, messageAsMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("OCM notification data transformation failed", e);
        }
    }

    private void checkResult(String result, final boolean useBetaTemplate) {
        if (useBetaTemplate) {
            assertTrue(result.contains("Cluster Manager - OpenShift"));
            assertTrue(result.contains("\u2796 Severity: None"));
            assertTrue(result.contains("1 event triggered."));
            assertTrue(result.contains("Explore this and others in **Cluster Manager**."));
            assertTrue(result.contains(CLUSTER_MANAGER_DEFAULT_EVENT_URL));
        } else {
            // check content from parent template
            AdaptiveCardValidatorHelper.validate(result);

            // check text message
            assertTrue(result.contains(EXPECTED_NOTIFICATION_TEXT_MESSAGE));
        }
    }
}
