package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestCostManagementTemplate {

    private static final Action ACTION = TestHelpers.createCostManagementAction();

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    CostManagement costManagement;

    @AfterEach
    void afterEach() {
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(false);
    }

    @Test
    public void testInstantMissingCostModelEmailTitle() {
        String result = generateEmail(costManagement.getTitle(CostManagement.MISSING_COST_MODEL, null));
        assertEquals("Source missing Cost Model", result);

         // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getTitle(CostManagement.MISSING_COST_MODEL, null));
        assertEquals("Instant notification - Missing cost model- Cost management - OpenShift", result);
    }

    @Test
    public void testInstantMissingCostModelEmailBody() {
        String result = generateEmail(costManagement.getBody(CostManagement.MISSING_COST_MODEL, null));
        assertTrue(result.contains("OpenShift source Dummy source name has no assigned cost model"));

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getBody(CostManagement.MISSING_COST_MODEL, null));
        assertTrue(result.contains("OpenShift source Dummy source name has no assigned cost model"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelCreateEmailTitle() {
        String result = generateEmail(costManagement.getTitle(CostManagement.COST_MODEL_CREATE, null));
        assertEquals("Cost Management cost model changed", result);

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getTitle(CostManagement.COST_MODEL_CREATE, null));
        assertEquals("Instant notification - Cost model changed - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelCreateEmailBody() {
        String result = generateEmail(costManagement.getBody(CostManagement.COST_MODEL_CREATE, null));
        assertTrue(result.contains("Cost model Sample model has been created."));

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getBody(CostManagement.COST_MODEL_CREATE, null));
        assertTrue(result.contains("Cost model Sample model has been created"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelUpdateEmailTitle() {
        String result = generateEmail(costManagement.getTitle(CostManagement.COST_MODEL_UPDATE, null));
        assertEquals("Cost Management cost model update", result);

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getTitle(CostManagement.COST_MODEL_UPDATE, null));
        assertEquals("Instant notification - Cost model update - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelUpdateEmailBody() {
        String result = generateEmail(costManagement.getBody(CostManagement.COST_MODEL_UPDATE, null));
        assertTrue(result.contains("Cost model Sample model has been updated."));

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getBody(CostManagement.COST_MODEL_UPDATE, null));
        assertTrue(result.contains("Cost model Sample model has been updated"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelRemoveEmailTitle() {
        String result = generateEmail(costManagement.getTitle(CostManagement.COST_MODEL_REMOVE, null));
        assertEquals("Cost Management cost model removal", result);

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getTitle(CostManagement.COST_MODEL_REMOVE, null));
        assertEquals("Instant notification - Cost model removal - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelRemoveEmailBody() {
        String result = generateEmail(costManagement.getBody(CostManagement.COST_MODEL_REMOVE, null));
        assertTrue(result.contains("Cost model Sample model has been removed."));

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getBody(CostManagement.COST_MODEL_REMOVE, null));
        assertTrue(result.contains("Cost model Sample model has been removed"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorStaleEmailTitle() {
        String result = generateEmail(costManagement.getTitle(CostManagement.CM_OPERATOR_STALE, null));
        assertEquals("Stale OpenShift cluster for Cost Management", result);

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getTitle(CostManagement.CM_OPERATOR_STALE, null));
        assertEquals("Instant notification - Stale cost management - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelOperatorStaleEmailBody() {
        String result = generateEmail(costManagement.getBody(CostManagement.CM_OPERATOR_STALE, null));
        assertTrue(result.contains("OpenShift source Dummy source name has not received any payloads in the last 3 or more days"));

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getBody(CostManagement.CM_OPERATOR_STALE, null));
        assertTrue(result.contains("OpenShift source Dummy source name has not received any payloads in the last 3 or more days"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorDataProcessedEmailTitle() {
        String result = generateEmail(costManagement.getTitle(CostManagement.CM_OPERATOR_DATA_PROCESSED, null));
        assertEquals("OpenShift cluster data processed by Cost Management", result);

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getTitle(CostManagement.CM_OPERATOR_DATA_PROCESSED, null));
        assertEquals("Instant notification - OpenShift cluster data processed - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelOperatorDataProcessedEmailBody() {
        String result = generateEmail(costManagement.getBody(CostManagement.CM_OPERATOR_DATA_PROCESSED, null));
        assertTrue(result.contains("Cost Management has completed processing for OpenShift source"));

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getBody(CostManagement.CM_OPERATOR_DATA_PROCESSED, null));
        assertTrue(result.contains("Cost Management has completed processing for OpenShift source"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorDataReceivedEmailTitle() {
        String result = generateEmail(costManagement.getTitle(CostManagement.CM_OPERATOR_DATA_RECEIVED, null));
        assertEquals("OpenShift cluster data received by Cost Management", result);

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getTitle(CostManagement.CM_OPERATOR_DATA_RECEIVED, null));
        assertEquals("Instant notification - OpenShift cluster data received - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelOperatorDataReceivedEmailBody() {
        String result = generateEmail(costManagement.getBody(CostManagement.CM_OPERATOR_DATA_RECEIVED, null));
        assertTrue(result.contains("OpenShift source Dummy source name has received a new payload and processing should begin shortly"));

        // test template V2
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        result = generateEmail(costManagement.getBody(CostManagement.CM_OPERATOR_DATA_RECEIVED, null));
        assertTrue(result.contains("OpenShift source Dummy source name has received a new payload and processing should begin shortly"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    private String generateEmail(TemplateInstance template) {
        return template
            .data("action", ACTION)
            .data("environment", environment)
            .render();
    }
}
