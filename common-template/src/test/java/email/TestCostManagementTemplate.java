package email;

import com.redhat.cloud.notifications.ingress.Action;
import helpers.TestHelpers;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestCostManagementTemplate extends EmailTemplatesRendererHelper {

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
    protected String getBundleDisplayName() {
        return "OpenShift";
    }

    @Override
    protected String getAppDisplayName() {
        return "Cost management";
    }

    @Test
    public void testInstantMissingCostModelEmailTitle() {
        eventTypeDisplayName = "Missing Openshift Cost Model";
        String result = generateEmailSubject(MISSING_COST_MODEL, ACTION);
        assertEquals("Instant notification - Missing Openshift Cost Model - Cost management - OpenShift", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantMissingCostModelEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(MISSING_COST_MODEL, ACTION, useBetaTemplate);
        if (useBetaTemplate) {
            assertTrue(result.contains("The OpenShift"));
            assertTrue(result.contains("Dummy source name"));
            assertTrue(result.contains("integration has no assigned cost model."));
        } else {
            assertTrue(result.contains("OpenShift source Dummy source name has no assigned cost model"));
        }
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelCreateEmailTitle() {
        eventTypeDisplayName = "Cost Model Create";
        String result = generateEmailSubject(COST_MODEL_CREATE, ACTION);
        assertEquals("Instant notification - Cost Model Create - Cost management - OpenShift", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantCostModelCreateEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(COST_MODEL_CREATE, ACTION, useBetaTemplate);
        assertTrue(result.contains("Cost model"));
        assertTrue(result.contains("Sample model"));
        assertTrue(result.contains("has been created"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelUpdateEmailTitle() {
        eventTypeDisplayName = "Cost Model Update";
        String result = generateEmailSubject(COST_MODEL_UPDATE, ACTION);
        assertEquals("Instant notification - Cost Model Update - Cost management - OpenShift", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantCostModelUpdateEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(COST_MODEL_UPDATE, ACTION, useBetaTemplate);
        if (useBetaTemplate) {
            assertTrue(result.contains("Cost model <b>Sample model</b> has been updated"));
        } else {
            assertTrue(result.contains("Cost model Sample model has been updated"));
        }
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelRemoveEmailTitle() {
        eventTypeDisplayName = "Cost Model Remove";
        String result = generateEmailSubject(COST_MODEL_REMOVE, ACTION);
        assertEquals("Instant notification - Cost Model Remove - Cost management - OpenShift", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantCostModelRemoveEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(COST_MODEL_REMOVE, ACTION, useBetaTemplate);
        if (useBetaTemplate) {
            assertTrue(result.contains("Cost model <b>Sample model</b> has been removed"));
        } else {
            assertTrue(result.contains("Cost model Sample model has been removed"));
        }
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorStaleEmailTitle() {
        eventTypeDisplayName = "CM Operator Stale Data";
        String result = generateEmailSubject(CM_OPERATOR_STALE, ACTION);
        assertEquals("Instant notification - CM Operator Stale Data - Cost management - OpenShift", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantCostModelOperatorStaleEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(CM_OPERATOR_STALE, ACTION, useBetaTemplate);
        assertTrue(result.contains("OpenShift source"));
        assertTrue(result.contains("Dummy source name"));
        assertTrue(result.contains("has not received any payloads in the last 3 or more days"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorDataProcessedEmailTitle() {
        eventTypeDisplayName = "CM Operator Data Processed";
        String result = generateEmailSubject(CM_OPERATOR_DATA_PROCESSED, ACTION);
        assertEquals("Instant notification - CM Operator Data Processed - Cost management - OpenShift", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantCostModelOperatorDataProcessedEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(CM_OPERATOR_DATA_PROCESSED, ACTION, useBetaTemplate);
        assertTrue(result.contains("Cost Management has completed processing for OpenShift source"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testInstantCostModelOperatorDataReceivedEmailTitle() {
        eventTypeDisplayName = "CM Operator Data Received";
        String result = generateEmailSubject(CM_OPERATOR_DATA_RECEIVED, ACTION);
        assertEquals("Instant notification - CM Operator Data Received - Cost management - OpenShift", result);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testInstantCostModelOperatorDataReceivedEmailBody(boolean useBetaTemplate) {
        String result = generateEmailBody(CM_OPERATOR_DATA_RECEIVED, ACTION, useBetaTemplate);
        if (useBetaTemplate) {
            assertTrue(result.contains("OpenShift source <b>Dummy source name</b> has received a new payload and processing should begin shortly"));
        } else {
            assertTrue(result.contains("OpenShift source Dummy source name has received a new payload and processing should begin shortly"));
        }
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
