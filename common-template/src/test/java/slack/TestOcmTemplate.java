package slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.qute.templates.Severity;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.OcmTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.SLACK;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TestOcmTemplate {

    private static final JsonObject MESSAGE = OcmTestHelpers.createOcmMessage("Atlantic", "OSDTrial", "<b>Altlantic</b> server is experiencing flooding issues", "Subject line!", Severity.CRITICAL.name());

    private static final String CLUSTER_MANAGER_DEFAULT_EVENT_URL = "https://cloud.redhat.com/openshift/details/s/2XqNHRdLNEAzshh7MkkOql6fx6I?from=notifications&integration=slack";

    @Inject
    TemplateService templateService;

    @Inject
    ObjectMapper objectMapper;

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testRenderedOcmTemplates(boolean useBetaTemplate) {
        String result = renderTemplate(null, MESSAGE, useBetaTemplate);
        assertTrue(result.contains(" - Cluster Manager - OpenShift"));
        assertTrue(result.contains("\u203C\uFE0F Severity: Critical"));
        assertTrue(result.contains("1 event triggered."));
        assertTrue(result.contains("Explore this and others in *<" + CLUSTER_MANAGER_DEFAULT_EVENT_URL + "|Cluster Manager>*."));
    }

    String renderTemplate(final String eventType, final JsonObject message, boolean useBetaTemplate) {
        TemplateDefinition templateConfig = new TemplateDefinition(SLACK, "openshift", "cluster-manager", eventType, useBetaTemplate);
        Map<String, Object> messageAsMap;
        try {
            messageAsMap = objectMapper.readValue(message.encode(), Map.class);
            return templateService.renderTemplate(templateConfig, messageAsMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("OCM notification data transformation failed", e);
        }
    }
}
