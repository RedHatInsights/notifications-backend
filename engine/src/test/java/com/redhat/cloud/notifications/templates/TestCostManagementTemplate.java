package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestCostManagementTemplate extends EmailTemplatesInDbHelper {

    private static final Action ACTION = TestHelpers.createCostManagementAction();

    @Inject
    FeatureFlipper featureFlipper;

    @AfterEach
    void afterEach() {
        featureFlipper.setCostManagementEmailTemplatesV2Enabled(false);
        migrate();
    }

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
        return List.of(CostManagement.MISSING_COST_MODEL, CostManagement.COST_MODEL_CREATE, CostManagement.COST_MODEL_UPDATE, CostManagement.COST_MODEL_REMOVE,
            CostManagement.CM_OPERATOR_STALE, CostManagement.CM_OPERATOR_DATA_PROCESSED, CostManagement.CM_OPERATOR_DATA_RECEIVED);
    }

    @Test
    public void testInstantMissingCostModelEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(CostManagement.MISSING_COST_MODEL, ACTION);
            assertEquals("Source missing Cost Model", result);

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(CostManagement.MISSING_COST_MODEL, ACTION);
            assertEquals("Instant notification - Missing cost model- Cost management - OpenShift", result);
        });
    }

    @Test
    public void testInstantMissingCostModelEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(CostManagement.MISSING_COST_MODEL, ACTION);
            assertTrue(result.contains("OpenShift source Dummy source name has no assigned cost model"));

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(CostManagement.MISSING_COST_MODEL, ACTION);
            assertTrue(result.contains("OpenShift source Dummy source name has no assigned cost model"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantCostModelCreateEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(CostManagement.COST_MODEL_CREATE, ACTION);
            assertEquals("Cost Management cost model changed", result);

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(CostManagement.COST_MODEL_CREATE, ACTION);
            assertEquals("Instant notification - Cost model changed - Cost management - OpenShift", result);
        });
    }

    @Test
    public void testInstantCostModelCreateEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(CostManagement.COST_MODEL_CREATE, ACTION);
            assertTrue(result.contains("Cost model Sample model has been created."));

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(CostManagement.COST_MODEL_CREATE, ACTION);
            assertTrue(result.contains("Cost model Sample model has been created"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantCostModelUpdateEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(CostManagement.COST_MODEL_UPDATE, ACTION);
            assertEquals("Cost Management cost model update", result);

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(CostManagement.COST_MODEL_UPDATE, ACTION);
            assertEquals("Instant notification - Cost model update - Cost management - OpenShift", result);
        });
    }

    @Test
    public void testInstantCostModelUpdateEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(CostManagement.COST_MODEL_UPDATE, ACTION);
            assertTrue(result.contains("Cost model Sample model has been updated."));

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(CostManagement.COST_MODEL_UPDATE, ACTION);
            assertTrue(result.contains("Cost model Sample model has been updated"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantCostModelRemoveEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(CostManagement.COST_MODEL_REMOVE, ACTION);
            assertEquals("Cost Management cost model removal", result);

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(CostManagement.COST_MODEL_REMOVE, ACTION);
            assertEquals("Instant notification - Cost model removal - Cost management - OpenShift", result);
        });
    }

    @Test
    public void testInstantCostModelRemoveEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(CostManagement.COST_MODEL_REMOVE, ACTION);
            assertTrue(result.contains("Cost model Sample model has been removed."));

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(CostManagement.COST_MODEL_REMOVE, ACTION);
            assertTrue(result.contains("Cost model Sample model has been removed"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantCostModelOperatorStaleEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(CostManagement.CM_OPERATOR_STALE, ACTION);
            assertEquals("Stale OpenShift cluster for Cost Management", result);

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(CostManagement.CM_OPERATOR_STALE, ACTION);
            assertEquals("Instant notification - Stale cost management - Cost management - OpenShift", result);
        });
    }

    @Test
    public void testInstantCostModelOperatorStaleEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(CostManagement.CM_OPERATOR_STALE, ACTION);
            assertTrue(result.contains("OpenShift source Dummy source name has not received any payloads in the last 3 or more days"));

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(CostManagement.CM_OPERATOR_STALE, ACTION);
            assertTrue(result.contains("OpenShift source Dummy source name has not received any payloads in the last 3 or more days"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantCostModelOperatorDataProcessedEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(CostManagement.CM_OPERATOR_DATA_PROCESSED, ACTION);
            assertEquals("OpenShift cluster data processed by Cost Management", result);

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(CostManagement.CM_OPERATOR_DATA_PROCESSED, ACTION);
            assertEquals("Instant notification - OpenShift cluster data processed - Cost management - OpenShift", result);
        });
    }

    @Test
    public void testInstantCostModelOperatorDataProcessedEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(CostManagement.CM_OPERATOR_DATA_PROCESSED, ACTION);
            assertTrue(result.contains("Cost Management has completed processing for OpenShift source"));

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(CostManagement.CM_OPERATOR_DATA_PROCESSED, ACTION);
            assertTrue(result.contains("Cost Management has completed processing for OpenShift source"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }

    @Test
    public void testInstantCostModelOperatorDataReceivedEmailTitle() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailSubject(CostManagement.CM_OPERATOR_DATA_RECEIVED, ACTION);
            assertEquals("OpenShift cluster data received by Cost Management", result);

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailSubject(CostManagement.CM_OPERATOR_DATA_RECEIVED, ACTION);
            assertEquals("Instant notification - OpenShift cluster data received - Cost management - OpenShift", result);
        });
    }

    @Test
    public void testInstantCostModelOperatorDataReceivedEmailBody() {
        statelessSessionFactory.withSession(statelessSession -> {
            String result = generateEmailBody(CostManagement.CM_OPERATOR_DATA_RECEIVED, ACTION);
            assertTrue(result.contains("OpenShift source Dummy source name has received a new payload and processing should begin shortly"));

            featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
            migrate();
            result = generateEmailBody(CostManagement.CM_OPERATOR_DATA_RECEIVED, ACTION);
            assertTrue(result.contains("OpenShift source Dummy source name has received a new payload and processing should begin shortly"));
            assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
        });
    }
}
