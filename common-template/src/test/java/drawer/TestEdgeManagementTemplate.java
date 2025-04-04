package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.qute.templates.extensions.TimeAgoFormatter;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestEdgeManagementTemplate {

    static final String IMAGE_CREATION = "image-creation";
    static final String UPDATE_DEVICES = "update-devices";
    private static final Action ACTION = TestHelpers.createEdgeManagementAction();

    @Inject
    TemplateService templateService;

    @Test
    void testRenderedTemplateImageCreation() {
        TimeAgoFormatter timeFormatter = new TimeAgoFormatter();
        String deltaTme = timeFormatter.format(LocalDateTime.now(UTC), LocalDateTime.from(ACTION.getTimestamp()));
        String result = renderTemplate(IMAGE_CREATION, ACTION);
        assertEquals(String.format("A new image named **Test name** was created %s.", deltaTme), result);
    }

    @Test
    void testRenderedTemplateUpdateDevice() {
        String result = renderTemplate(UPDATE_DEVICES, ACTION);
        assertEquals("An Update for the device **DEVICE-9012** started.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "rhel", "edge-management", eventType);
        return templateService.renderTemplate(templateConfig, action);
    }
}
