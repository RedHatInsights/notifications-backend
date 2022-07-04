package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

// Name needs to be "CostManagement" to read templates from resources/templates/CostManagement
public class CostManagement implements EmailTemplate {

    private static final String MISSING_COST_MODEL = "missing-cost-model";

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(MISSING_COST_MODEL)) {
                return Templates.MissingCostModelEmailTitle();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for CostManagement event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(MISSING_COST_MODEL)) {
                return Templates.MissingCostModelEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for CostManagement event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return eventType.equals(MISSING_COST_MODEL) && type == EmailSubscriptionType.INSTANT;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance MissingCostModelEmailTitle();

        public static native TemplateInstance MissingCostModelEmailBody();
    }

}
