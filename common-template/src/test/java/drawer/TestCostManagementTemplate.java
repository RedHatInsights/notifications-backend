package drawer;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TestCostManagementTemplate {

    static final String MISSING_COST_MODEL = "missing-cost-model";
    static final String COST_MODEL_CREATE = "cost-model-create";
    static final String COST_MODEL_UPDATE = "cost-model-update";
    static final String COST_MODEL_REMOVE = "cost-model-remove";
    static final String CM_OPERATOR_STALE = "cm-operator-stale";
    static final String CM_OPERATOR_DATA_PROCESSED = "cm-operator-data-processed";
    static final String CM_OPERATOR_DATA_RECEIVED = "cm-operator-data-received";
    private static final Action ACTION = TestHelpers.createCostManagementAction();

    @Inject
    TestHelpers testHelpers;

    @Test
    void testRenderedTemplateMissingCostModel() {
        String result = renderTemplate(MISSING_COST_MODEL, ACTION);
        assertEquals("OpenShift source **[Dummy source name](https://localhost/settings/sources/detail/12345?from=notifications&integration=drawer)** has no assigned cost model.", result);
    }

    @Test
    void testRenderedTemplateCostModelCreate() {
        String result = renderTemplate(COST_MODEL_CREATE, ACTION);
        assertEquals("Cost model **[Sample model](https://localhost/openshift/cost-management/cost-models/4540543DGE?from=notifications&integration=drawer)** has been created.", result);
    }

    @Test
    void testRenderedTemplateCostModelUpdate() {
        String result = renderTemplate(COST_MODEL_UPDATE, ACTION);
        assertEquals("Cost model **[Sample model](https://localhost/openshift/cost-management/cost-models/4540543DGE?from=notifications&integration=drawer)** has been updated.", result);
    }

    @Test
    void testRenderedTemplateCostModelRemove() {
        String result = renderTemplate(COST_MODEL_REMOVE, ACTION);
        assertEquals("Cost model **Sample model** has been removed. [Open Cost Management](https://localhost/openshift/cost-management/cost-models?from=notifications&integration=drawer)", result);
    }

    @Test
    void testRenderedTemplateCostModelOperatorStale() {
        String result = renderTemplate(CM_OPERATOR_STALE, ACTION);
        assertEquals("OpenShift source **[Dummy source name](https://localhost/settings/sources/detail/12345?from=notifications&integration=drawer)** has not received any payloads in the last 3 or more days.", result);
    }

    @Test
    void testRenderedTemplateCostModelOperatorDataProcessed() {
        String result = renderTemplate(CM_OPERATOR_DATA_PROCESSED, ACTION);
        assertEquals("Cost Management has completed processing for OpenShift source **[Dummy source name](https://localhost/openshift/cost-management/ocp/?from=notifications&integration=drawer)**.", result);
    }

    @Test
    void testRenderedTemplateCostModelOperatorDataReceived() {
        String result = renderTemplate(CM_OPERATOR_DATA_RECEIVED, ACTION);
        assertEquals("OpenShift source **[Dummy source name](https://localhost/openshift/cost-management?from=notifications&integration=drawer)** has received a new payload and processing should begin shortly.", result);
    }

    String renderTemplate(final String eventType, final Action action) {
        TemplateDefinition templateConfig = new TemplateDefinition(IntegrationType.DRAWER, "openshift", "cost-management", eventType);
        return testHelpers.renderTemplate(templateConfig, action);
    }
}
