package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "CostManagement" to read templates from resources/templates/CostManagement
public class CostManagement implements EmailTemplate {

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        switch (eventType) {
            case "missing-cost-model":
                return Templates.MissingCostModelEmailTitle();
            case "cost-model-create":
                return Templates.CostModelCreateEmailTitle();
            case "cost-model-update":
                return Templates.CostModelUpdateEmailTitle();
            case "cost-model-remove":
                return Templates.CostModelRemoveEmailTitle();
            case "cm-operator-stale":
                return Templates.CmOperatorStaleEmailTitle();
            case "cm-operator-data-processed":
                return Templates.CmOperatorDataProcessedEmailTitle();
            case "cm-operator-data-received":
                return Templates.CmOperatorDataReceivedEmailTitle();
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
            case "missing-cost-model":
                return Templates.MissingCostModelEmailBody();
            case "cost-model-create":
                return Templates.CostModelCreateEmailBody();
            case "cost-model-update":
                return Templates.CostModelUpdateEmailBody();
            case "cost-model-remove":
                return Templates.CostModelRemoveEmailBody();
            case "cm-operator-stale":
                return Templates.CmOperatorStaleEmailBody();
            case "cm-operator-data-processed":
                return Templates.CmOperatorDataProcessedEmailBody();
            case "cm-operator-data-received":
                return Templates.CmOperatorDataReceivedEmailBody();
            default:
                throw new UnsupportedOperationException(String.format(
                    "No email title template for CostManagement event_type: %s found.",
                    eventType
                ));
        }
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
    }

}
