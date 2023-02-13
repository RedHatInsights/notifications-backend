package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "AdvisorOpenshift" to read templates from resources/templates/AdvisorOpenshift
@ApplicationScoped
public class AdvisorOpenshift implements EmailTemplate {

    public static final String NEW_RECOMMENDATION = "new-recommendation";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(NEW_RECOMMENDATION)) {
                return getNewRecommendationInstantEmailTitle();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for OpenShift Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(NEW_RECOMMENDATION)) {
                return getNewRecommendationInstantEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for OpenShift Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    private TemplateInstance getNewRecommendationInstantEmailTitle() {
        if (featureFlipper.isAdvisorOpenShiftEmailTemplatesV2Enabled()) {
            return Templates.newRecommendationInstantEmailTitleV2();
        }
        return Templates.newRecommendationInstantEmailTitle();
    }

    private TemplateInstance getNewRecommendationInstantEmailBody() {
        if (featureFlipper.isAdvisorOpenShiftEmailTemplatesV2Enabled()) {
            return Templates.newRecommendationInstantEmailBodyV2();
        }
        return Templates.newRecommendationInstantEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (eventType.equals(NEW_RECOMMENDATION)) && type == EmailSubscriptionType.INSTANT;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newRecommendationInstantEmailTitle();

        public static native TemplateInstance newRecommendationInstantEmailBody();

        public static native TemplateInstance newRecommendationInstantEmailTitleV2();

        public static native TemplateInstance newRecommendationInstantEmailBodyV2();
    }

}
