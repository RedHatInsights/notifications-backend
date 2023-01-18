package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "CostManagement" to read templates from resources/templates/CostManagement
@ApplicationScoped
public class CostManagement implements EmailTemplate {

    public static final String MISSING_COST_MODEL = "missing-cost-model";
    public static final String COST_MODEL_CREATE = "cost-model-create";
    public static final String COST_MODEL_UPDATE = "cost-model-update";
    public static final String COST_MODEL_REMOVE = "cost-model-remove";
    public static final String CM_OPERATOR_STALE = "cm-operator-stale";
    public static final String CM_OPERATOR_DATA_PROCESSED = "cm-operator-data-processed";
    public static final String CM_OPERATOR_DATA_RECEIVED = "cm-operator-data-received";
    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        switch (eventType) {
            case MISSING_COST_MODEL:
                return getMissingCostModelEmailTitle();
            case COST_MODEL_CREATE:
                return getCostModelCreateEmailTitle();
            case COST_MODEL_UPDATE:
                return getCostModelUpdateEmailTitle();
            case COST_MODEL_REMOVE:
                return getCostModelRemoveEmailTitle();
            case CM_OPERATOR_STALE:
                return getCmOperatorStaleEmailTitle();
            case CM_OPERATOR_DATA_PROCESSED:
                return getCmOperatorDataProcessedEmailTitle();
            case CM_OPERATOR_DATA_RECEIVED:
                return getCmOperatorDataReceivedEmailTitle();
            default:
                throw new UnsupportedOperationException(String.format(
                    "No email title template for CostManagement event_type: %s found.",
                    eventType
                ));
        }
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        switch (eventType) {
            case MISSING_COST_MODEL:
                return getMissingCostModelEmailBody();
            case COST_MODEL_CREATE:
                return getCostModelCreateEmailBody();
            case COST_MODEL_UPDATE:
                return getCostModelUpdateEmailBody();
            case COST_MODEL_REMOVE:
                return getCostModelRemoveEmailBody();
            case CM_OPERATOR_STALE:
                return getCmOperatorStaleEmailBody();
            case CM_OPERATOR_DATA_PROCESSED:
                return getCmOperatorDataProcessedEmailBody();
            case CM_OPERATOR_DATA_RECEIVED:
                return getCmOperatorDataReceivedEmailBody();
            default:
                throw new UnsupportedOperationException(String.format(
                    "No email title template for CostManagement event_type: %s found.",
                    eventType
                ));
        }
    }

    private TemplateInstance getMissingCostModelEmailTitle() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.MissingCostModelEmailTitleV2();
        }
        return CostManagement.Templates.MissingCostModelEmailTitle();
    }

    private TemplateInstance getCostModelCreateEmailTitle() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CostModelCreateEmailTitleV2();
        }
        return CostManagement.Templates.CostModelCreateEmailTitle();
    }

    private TemplateInstance getCostModelUpdateEmailTitle() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CostModelUpdateEmailTitleV2();
        }
        return CostManagement.Templates.CostModelUpdateEmailTitle();
    }

    private TemplateInstance getCostModelRemoveEmailTitle() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CostModelRemoveEmailTitleV2();
        }
        return CostManagement.Templates.CostModelRemoveEmailTitle();
    }

    private TemplateInstance getCmOperatorStaleEmailTitle() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CmOperatorStaleEmailTitleV2();
        }
        return CostManagement.Templates.CmOperatorStaleEmailTitle();
    }

    private TemplateInstance getCmOperatorDataProcessedEmailTitle() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CmOperatorDataProcessedEmailTitleV2();
        }
        return CostManagement.Templates.CmOperatorDataProcessedEmailTitle();
    }

    private TemplateInstance getCmOperatorDataReceivedEmailTitle() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CmOperatorDataReceivedEmailTitleV2();
        }
        return CostManagement.Templates.CmOperatorDataReceivedEmailTitle();
    }

    private TemplateInstance getMissingCostModelEmailBody() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.MissingCostModelEmailBodyV2();
        }
        return CostManagement.Templates.MissingCostModelEmailBody();
    }

    private TemplateInstance getCostModelCreateEmailBody() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CostModelCreateEmailBodyV2();
        }
        return CostManagement.Templates.CostModelCreateEmailBody();
    }

    private TemplateInstance getCostModelUpdateEmailBody() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CostModelUpdateEmailBodyV2();
        }
        return CostManagement.Templates.CostModelUpdateEmailBody();
    }

    private TemplateInstance getCostModelRemoveEmailBody() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CostModelRemoveEmailBodyV2();
        }
        return CostManagement.Templates.CostModelRemoveEmailBody();
    }

    private TemplateInstance getCmOperatorStaleEmailBody() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CmOperatorStaleEmailBodyV2();
        }
        return CostManagement.Templates.CmOperatorStaleEmailBody();
    }

    private TemplateInstance getCmOperatorDataProcessedEmailBody() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CmOperatorDataProcessedEmailBodyV2();
        }
        return CostManagement.Templates.CmOperatorDataProcessedEmailBody();
    }

    private TemplateInstance getCmOperatorDataReceivedEmailBody() {
        if (featureFlipper.isCostManagementEmailTemplatesV2Enabled()) {
            return CostManagement.Templates.CmOperatorDataReceivedEmailBodyV2();
        }
        return CostManagement.Templates.CmOperatorDataReceivedEmailBody();
    }


    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance MissingCostModelEmailTitle();

        public static native TemplateInstance MissingCostModelEmailBody();

        public static native TemplateInstance CostModelCreateEmailTitle();

        public static native TemplateInstance CostModelCreateEmailBody();

        public static native TemplateInstance CostModelUpdateEmailTitle();

        public static native TemplateInstance CostModelUpdateEmailBody();

        public static native TemplateInstance CostModelRemoveEmailTitle();

        public static native TemplateInstance CostModelRemoveEmailBody();

        public static native TemplateInstance CmOperatorStaleEmailTitle();

        public static native TemplateInstance CmOperatorStaleEmailBody();

        public static native TemplateInstance CmOperatorDataProcessedEmailTitle();

        public static native TemplateInstance CmOperatorDataProcessedEmailBody();

        public static native TemplateInstance CmOperatorDataReceivedEmailTitle();

        public static native TemplateInstance CmOperatorDataReceivedEmailBody();

        public static native TemplateInstance MissingCostModelEmailTitleV2();

        public static native TemplateInstance MissingCostModelEmailBodyV2();

        public static native TemplateInstance CostModelCreateEmailTitleV2();

        public static native TemplateInstance CostModelCreateEmailBodyV2();

        public static native TemplateInstance CostModelUpdateEmailTitleV2();

        public static native TemplateInstance CostModelUpdateEmailBodyV2();

        public static native TemplateInstance CostModelRemoveEmailTitleV2();

        public static native TemplateInstance CostModelRemoveEmailBodyV2();

        public static native TemplateInstance CmOperatorStaleEmailTitleV2();

        public static native TemplateInstance CmOperatorStaleEmailBodyV2();

        public static native TemplateInstance CmOperatorDataProcessedEmailTitleV2();

        public static native TemplateInstance CmOperatorDataProcessedEmailBodyV2();

        public static native TemplateInstance CmOperatorDataReceivedEmailTitleV2();

        public static native TemplateInstance CmOperatorDataReceivedEmailBodyV2();
    }

}
