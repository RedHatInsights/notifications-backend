package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestCostManagementTemplate extends EmailTemplatesInDbHelper {

    static final String MISSING_COST_MODEL = "missing-cost-model";
    static final String COST_MODEL_CREATE = "cost-model-create";
    static final String COST_MODEL_UPDATE = "cost-model-update";
    static final String COST_MODEL_REMOVE = "cost-model-remove";
    static final String CM_OPERATOR_STALE = "cm-operator-stale";
    static final String CM_OPERATOR_DATA_PROCESSED = "cm-operator-data-processed";
    static final String CM_OPERATOR_DATA_RECEIVED = "cm-operator-data-received";
    private static final Action ACTION = TestHelpers.createCostManagementAction();

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

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
        return List.of(MISSING_COST_MODEL, COST_MODEL_CREATE, COST_MODEL_UPDATE, COST_MODEL_REMOVE,
            CM_OPERATOR_STALE, CM_OPERATOR_DATA_PROCESSED, CM_OPERATOR_DATA_RECEIVED);
    }

    @Test
    public void testInstantMissingCostModelEmailTitle() {
        String result = generateEmailSubject(MISSING_COST_MODEL, ACTION);
        assertEquals("Source missing Cost Model", result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(MISSING_COST_MODEL, ACTION);
        assertEquals("Instant notification - Missing cost model- Cost management - OpenShift", result);
    }

    @Test
    public void testInstantMissingCostModelEmailBody() {
        String result = generateEmailBody(MISSING_COST_MODEL, ACTION);
        assertTrue(result.contains("OpenShift source Dummy source name has no assigned cost model"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(MISSING_COST_MODEL, ACTION);
        assertTrue(result.contains("OpenShift source Dummy source name has no assigned cost model"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelCreateEmailTitle() {
        String result = generateEmailSubject(COST_MODEL_CREATE, ACTION);
        assertEquals("Cost Management cost model changed", result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(COST_MODEL_CREATE, ACTION);
        assertEquals("Instant notification - Cost model changed - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelCreateEmailBody() {
        String result = generateEmailBody(COST_MODEL_CREATE, ACTION);
        assertTrue(result.contains("Cost model Sample model has been created."));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(COST_MODEL_CREATE, ACTION);
        assertTrue(result.contains("Cost model Sample model has been created"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelUpdateEmailTitle() {
        String result = generateEmailSubject(COST_MODEL_UPDATE, ACTION);
        assertEquals("Cost Management cost model update", result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(COST_MODEL_UPDATE, ACTION);
        assertEquals("Instant notification - Cost model update - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelUpdateEmailBody() {
        String result = generateEmailBody(COST_MODEL_UPDATE, ACTION);
        assertTrue(result.contains("Cost model Sample model has been updated."));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(COST_MODEL_UPDATE, ACTION);
        assertTrue(result.contains("Cost model Sample model has been updated"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelRemoveEmailTitle() {
        String result = generateEmailSubject(COST_MODEL_REMOVE, ACTION);
        assertEquals("Cost Management cost model removal", result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(COST_MODEL_REMOVE, ACTION);
        assertEquals("Instant notification - Cost model removal - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelRemoveEmailBody() {
        String result = generateEmailBody(COST_MODEL_REMOVE, ACTION);
        assertTrue(result.contains("Cost model Sample model has been removed."));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(COST_MODEL_REMOVE, ACTION);
        assertTrue(result.contains("Cost model Sample model has been removed"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorStaleEmailTitle() {
        String result = generateEmailSubject(CM_OPERATOR_STALE, ACTION);
        assertEquals("Stale OpenShift cluster for Cost Management", result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(CM_OPERATOR_STALE, ACTION);
        assertEquals("Instant notification - Stale cost management - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelOperatorStaleEmailBody() {
        String result = generateEmailBody(CM_OPERATOR_STALE, ACTION);
        assertTrue(result.contains("OpenShift source Dummy source name has not received any payloads in the last 3 or more days"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(CM_OPERATOR_STALE, ACTION);
        assertTrue(result.contains("OpenShift source Dummy source name has not received any payloads in the last 3 or more days"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorDataProcessedEmailTitle() {
        String result = generateEmailSubject(CM_OPERATOR_DATA_PROCESSED, ACTION);
        assertEquals("OpenShift cluster data processed by Cost Management", result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(CM_OPERATOR_DATA_PROCESSED, ACTION);
        assertEquals("Instant notification - OpenShift cluster data processed - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelOperatorDataProcessedEmailBody() {
        String result = generateEmailBody(CM_OPERATOR_DATA_PROCESSED, ACTION);
        assertTrue(result.contains("Cost Management has completed processing for OpenShift source"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(CM_OPERATOR_DATA_PROCESSED, ACTION);
        assertTrue(result.contains("Cost Management has completed processing for OpenShift source"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorDataReceivedEmailTitle() {
        String result = generateEmailSubject(CM_OPERATOR_DATA_RECEIVED, ACTION);
        assertEquals("OpenShift cluster data received by Cost Management", result);

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(CM_OPERATOR_DATA_RECEIVED, ACTION);
        assertEquals("Instant notification - OpenShift cluster data received - Cost management - OpenShift", result);
    }

    @Test
    public void testInstantCostModelOperatorDataReceivedEmailBody() {
        String result = generateEmailBody(CM_OPERATOR_DATA_RECEIVED, ACTION);
        assertTrue(result.contains("OpenShift source Dummy source name has received a new payload and processing should begin shortly"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setCostManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(CM_OPERATOR_DATA_RECEIVED, ACTION);
        assertTrue(result.contains("OpenShift source Dummy source name has received a new payload and processing should begin shortly"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
