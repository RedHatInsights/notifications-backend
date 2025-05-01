package slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import helpers.OcmTestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.SLACK;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestOcmTemplate {

    private static final JsonObject MESSAGE = OcmTestHelpers.createOcmMessage("Atlantic", "OSDTrial", "<b>Altlantic</b> server is experiencing flooding issues", "Subject line!");

    private static final String CLUSTER_MANAGER_DEFAULT_EVENT_URL = "http://localhost/openshift/details/s/2XqNHRdLNEAzshh7MkkOql6fx6I?from=notifications&integration=slack";

    @Inject
    TemplateService templateService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void testRenderedOcmTemplates() {
        String result = renderTemplate(null, MESSAGE);
        checkResult(null, result);
    }

    String renderTemplate(final String eventType, final JsonObject message) {
        TemplateDefinition templateConfig = new TemplateDefinition(SLACK, "openshift", "cluster-manager", eventType);
        Map<String, Object> messageAsMap;
        try {
            messageAsMap = objectMapper.readValue(message.encode(), Map.class);
            return templateService.renderTemplate(templateConfig, messageAsMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("OCM notification data transformation failed", e);
        }
    }

    private void checkResult(String eventType, String result) {
        switch (eventType) {
            case null -> assertEquals("1 event triggered from Cluster Manager - OpenShift. <" + CLUSTER_MANAGER_DEFAULT_EVENT_URL + "|Open Cluster Manager>", result);
            default -> throw new IllegalArgumentException(eventType + "is not a valid event type");
        }
    }
}
