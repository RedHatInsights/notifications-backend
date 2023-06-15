package com.redhat.cloud.notifications.templates;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.Map;

import static com.redhat.cloud.notifications.models.IntegrationTemplate.TemplateKind.ORG;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TestDrawerDefaultTemplate  {

    @Inject
    TemplateRepository templateRepository;

    @Inject
    TemplateService templateService;

    @Inject
    ObjectMapper objectMapper;

    @Test
    public void testInstantEmailTitle() throws JsonProcessingException {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");

        JsonObject data = TestHelpers.wrapActionToJsonObject(action);

        Map<Object, Object> dataAsMap = objectMapper.readValue(data.encode(), Map.class);

        String result = getDefaultDrawerTemplate()
            .data("data", dataAsMap)
            .render();
        assertEquals("FooMachine triggered 2 events", result);
    }

    private TemplateInstance getDefaultDrawerTemplate() {
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(null, null, ORG, "drawer")
            .orElseThrow(() -> new IllegalStateException("No default template defined"));
        String template = integrationTemplate.getTheTemplate().getData();
        return templateService.compileTemplate(template, integrationTemplate.getTheTemplate().getName());
    }
}
