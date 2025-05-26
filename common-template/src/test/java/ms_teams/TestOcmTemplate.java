package ms_teams;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.qute.templates.mapping.OpenShift;
import helpers.OcmTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

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

    private static final JsonObject MESSAGE = OcmTestHelpers.createOcmMessage("Atlantic", "OSDTrial", "<b>Altlantic</b> server is experiencing flooding issues", "Subject line!");

    private static final String CLUSTER_MANAGER_DEFAULT_EVENT_URL = "https://cloud.redhat.com/openshift/details/s/2XqNHRdLNEAzshh7MkkOql6fx6I?from=notifications&integration=teams";
    public static final String EXPECTED_NOTIFICATION_TEXT_MESSAGE = "\"text\": \"1 event triggered from Cluster Manager - OpenShift. [Open Cluster Manager](" + CLUSTER_MANAGER_DEFAULT_EVENT_URL + ")\"";

    @Inject
    TemplateService templateService;

    @Inject
    ObjectMapper objectMapper;

    @ValueSource(strings = { CLUSTER_MANAGER_CLUSTER_UPDATE, CLUSTER_MANAGER_CLUSTER_LIFECYCLE, CLUSTER_MANAGER_CLUSTER_CONFIGURATION,
        CLUSTER_MANAGER_CLUSTER_SUBSCRIPTION, CLUSTER_MANAGER_CLUSTER_OWNERSHIP, CLUSTER_MANAGER_CLUSTER_ACCESS, CLUSTER_MANAGER_CLUSTER_SCALING,
        CLUSTER_MANAGER_CAPACITY_MANAGEMENT, CLUSTER_MANAGER_CLUSTER_SECURITY, CLUSTER_MANAGER_CLUSTER_ADD_ON, CLUSTER_MANAGER_CUSTOMER_SUPPORT,
        CLUSTER_MANAGER_CLUSTER_NETWORKING, CLUSTER_MANAGER_GENERAL_NOTIFICATION
    })
    @ParameterizedTest
    void testRenderedOcmTemplates(final String eventType) {
        String result = renderTemplate(eventType);
        checkResult(result);
    }

    String renderTemplate(final String eventType) {
        TemplateDefinition templateConfig = new TemplateDefinition(MS_TEAMS, OpenShift.BUNDLE_NAME, CLUSTER_MANAGER_APP_NAME, eventType);
        Map<String, Object> messageAsMap;
        try {
            messageAsMap = objectMapper.readValue(MESSAGE.encode(), Map.class);
            return templateService.renderTemplate(templateConfig, messageAsMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("OCM notification data transformation failed", e);
        }
    }

    private void checkResult(String result) {
        // check content from parent template
        AdaptiveCardValidatorHelper.validate(result);

        // check text message
        assertTrue(result.contains(EXPECTED_NOTIFICATION_TEXT_MESSAGE));
    }
}
