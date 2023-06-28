package com.redhat.cloud.notifications.templates.drawer;

import com.redhat.cloud.notifications.IntegrationTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TestCostManagementTemplate extends IntegrationTemplatesInDbHelper {

    static final String MISSING_COST_MODEL = "missing-cost-model";
    static final String COST_MODEL_CREATE = "cost-model-create";
    static final String COST_MODEL_UPDATE = "cost-model-update";
    static final String COST_MODEL_REMOVE = "cost-model-remove";
    static final String CM_OPERATOR_STALE = "cm-operator-stale";
    static final String CM_OPERATOR_DATA_PROCESSED = "cm-operator-data-processed";
    static final String CM_OPERATOR_DATA_RECEIVED = "cm-operator-data-received";
    private static final Action ACTION = TestHelpers.createCostManagementAction();

    @Override
    protected String getBundle() {
        return "openshift";
    }

    @Override
    protected String getApp() {
        return "cost-management";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(MISSING_COST_MODEL, COST_MODEL_CREATE, COST_MODEL_UPDATE, COST_MODEL_REMOVE,
            CM_OPERATOR_STALE, CM_OPERATOR_DATA_PROCESSED, CM_OPERATOR_DATA_RECEIVED);
    }

    @Test
    void testRenderedTemplateMissingCostModel() {

        String result = generateDrawerTemplate(MISSING_COST_MODEL, ACTION);
        assertEquals("OpenShift source <b>Dummy source name</b> has no assigned cost model.", result);
    }

    @Test
    public void testRenderedTemplateCostModelCreate() {
        String result = generateDrawerTemplate(COST_MODEL_CREATE, ACTION);
        assertEquals("Cost model <b>Sample model</b> has been created.", result);
    }

    @Test
    public void testRenderedTemplateCostModelUpdate() {
        String result = generateDrawerTemplate(COST_MODEL_UPDATE, ACTION);
        assertEquals("Cost model <b>Sample model</b> has been updated.", result);
    }

    @Test
    public void testRenderedTemplateCostModelRemove() {
        String result = generateDrawerTemplate(COST_MODEL_REMOVE, ACTION);
        assertEquals("Cost model <b>Sample model</b> has been removed.", result);
    }

    @Test
    public void testRenderedTemplateCostModelOperatorStale() {
        String result = generateDrawerTemplate(CM_OPERATOR_STALE, ACTION);
        assertEquals("OpenShift source <b>Dummy source name</b> has not received any payloads in the last 3 or more days.", result);
    }

    @Test
    public void testRenderedTemplateCostModelOperatorDataProcessed() {
        String result = generateDrawerTemplate(CM_OPERATOR_DATA_PROCESSED, ACTION);
        assertEquals("Cost Management has completed processing for OpenShift source <b>Dummy source name</b>.", result);
    }

    @Test
    public void testRenderedTemplateCostModelOperatorDataReceived() {
        String result = generateDrawerTemplate(CM_OPERATOR_DATA_RECEIVED, ACTION);
        assertEquals("OpenShift source <b>Dummy source name</b> has received a new payload and processing should begin shortly.", result);
    }
}
