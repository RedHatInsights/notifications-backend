package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.DEACTIVATED_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.NEW_RECOMMENDATION;
import static com.redhat.cloud.notifications.processors.email.aggregators.AdvisorEmailAggregator.RESOLVED_RECOMMENDATION;

// Name needs to be "Advisor" to read templates from resources/templates/Advisor
@ApplicationScoped
public class Advisor implements EmailTemplate {

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(NEW_RECOMMENDATION)) {
                return getNewRecommendationInstantEmailTitle();
            } else if (eventType.equals(RESOLVED_RECOMMENDATION)) {
                return getResolvedRecommendationInstantEmailTitle();
            } else if (eventType.equals(DEACTIVATED_RECOMMENDATION)) {
                return getDeactivatedRecommendationInstantEmailTitle();
            }
        } else if (type == EmailSubscriptionType.DAILY) {
            return getDailyEmailTitle();
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(NEW_RECOMMENDATION)) {
                return getNewRecommendationInstantEmailBody();
            } else if (eventType.equals(RESOLVED_RECOMMENDATION)) {
                return getResolvedRecommendationInstantEmailBody();
            } else if (eventType.equals(DEACTIVATED_RECOMMENDATION)) {
                return getDeactivatedRecommendationInstantEmailBody();
            }
        } else if (type == EmailSubscriptionType.DAILY) {
            return getDailyEmailBody();
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Advisor event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    private TemplateInstance getNewRecommendationInstantEmailTitle() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.newRecommendationInstantEmailTitleV2();
        }
        return Templates.newRecommendationInstantEmailTitle();
    }

    private TemplateInstance getResolvedRecommendationInstantEmailTitle() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.resolvedRecommendationInstantEmailTitleV2();
        }
        return Templates.resolvedRecommendationInstantEmailTitle();
    }

    private TemplateInstance getDeactivatedRecommendationInstantEmailTitle() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.deactivatedRecommendationInstantEmailTitleV2();
        }
        return Templates.deactivatedRecommendationInstantEmailTitle();
    }

    private TemplateInstance getDailyEmailTitle() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailTitleV2();
        }
        return Templates.dailyEmailTitle();
    }

    private TemplateInstance getNewRecommendationInstantEmailBody() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.newRecommendationInstantEmailBodyV2();
        }
        return Templates.newRecommendationInstantEmailBody();
    }

    private TemplateInstance getResolvedRecommendationInstantEmailBody() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.resolvedRecommendationInstantEmailBodyV2();
        }
        return Templates.resolvedRecommendationInstantEmailBody();
    }

    private TemplateInstance getDeactivatedRecommendationInstantEmailBody() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.deactivatedRecommendationInstantEmailBodyV2();
        }
        return Templates.deactivatedRecommendationInstantEmailBody();
    }

    private TemplateInstance getDailyEmailBody() {
        if (featureFlipper.isAdvisorEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailBodyV2();
        }
        return Templates.dailyEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (
            type == EmailSubscriptionType.DAILY && isDailyDigestEnabled() ||
            type == EmailSubscriptionType.INSTANT && (
                eventType.equals(NEW_RECOMMENDATION) ||
                eventType.equals(RESOLVED_RECOMMENDATION) ||
                eventType.equals(DEACTIVATED_RECOMMENDATION)
            ));
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT || type == EmailSubscriptionType.DAILY && isDailyDigestEnabled();
    }

    /*
     * This is a feature flag meant to disable the daily digest on prod until it has been fully validated on stage.
     * TODO Remove this as soon as the daily digest is enabled on prod.
     */
    private boolean isDailyDigestEnabled() {
        return featureFlipper.isRhelAdvisorDailyDigestEnabled();
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newRecommendationInstantEmailTitle();

        public static native TemplateInstance newRecommendationInstantEmailBody();

        public static native TemplateInstance resolvedRecommendationInstantEmailTitle();

        public static native TemplateInstance resolvedRecommendationInstantEmailBody();

        public static native TemplateInstance deactivatedRecommendationInstantEmailTitle();

        public static native TemplateInstance deactivatedRecommendationInstantEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();

        public static native TemplateInstance newRecommendationInstantEmailTitleV2();

        public static native TemplateInstance newRecommendationInstantEmailBodyV2();

        public static native TemplateInstance resolvedRecommendationInstantEmailTitleV2();

        public static native TemplateInstance resolvedRecommendationInstantEmailBodyV2();

        public static native TemplateInstance deactivatedRecommendationInstantEmailTitleV2();

        public static native TemplateInstance deactivatedRecommendationInstantEmailBodyV2();

        public static native TemplateInstance dailyEmailTitleV2();

        public static native TemplateInstance dailyEmailBodyV2();
    }

}
