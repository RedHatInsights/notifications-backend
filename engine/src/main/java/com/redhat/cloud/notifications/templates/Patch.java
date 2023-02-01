package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class Patch implements EmailTemplate {

    protected static final String NEW_ADVISORY = "new-advisory";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(Patch.NEW_ADVISORY)) {
                return getNewAdvisoriesInstantEmailTitle();
            }
        } else if (type == EmailSubscriptionType.DAILY) {
            return getDailyEmailTitle();
        }

        throw new UnsupportedOperationException(String.format(
        "No email title template for Patch event_type: %s and EmailSubscription: %s found.",
        eventType, type));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(Patch.NEW_ADVISORY)) {
                return getNewAdvisoriesInstantEmailBody();
            }
        } else if (type == EmailSubscriptionType.DAILY) {
            return getDailyEmailBody();
        }

        throw new UnsupportedOperationException(String.format(
        "No email body template for Patch event_type: %s and EmailSubscription: %s found.",
        eventType, type));
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return (type == EmailSubscriptionType.INSTANT &&
                (eventType.equals(Patch.NEW_ADVISORY))) ||
                type == EmailSubscriptionType.DAILY;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT || type == EmailSubscriptionType.DAILY;
    }

    private TemplateInstance getNewAdvisoriesInstantEmailBody() {
        if (featureFlipper.isPatchEmailTemplatesV2Enabled()) {
            return Templates.newAdvisoriesInstantEmailBodyV2();
        }
        return Templates.newAdvisoriesInstantEmailBody();
    }

    private TemplateInstance getDailyEmailBody() {
        if (featureFlipper.isPatchEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailBodyV2();
        }
        return Templates.dailyEmailBody();
    }

    private TemplateInstance getNewAdvisoriesInstantEmailTitle() {
        if (featureFlipper.isPatchEmailTemplatesV2Enabled()) {
            return Templates.newAdvisoriesInstantEmailTitleV2();
        }
        return Templates.newAdvisoriesInstantEmailTitle();
    }

    private TemplateInstance getDailyEmailTitle() {
        if (featureFlipper.isPatchEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailTitleV2();
        }
        return Templates.dailyEmailTitle();
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newAdvisoriesInstantEmailTitle();

        public static native TemplateInstance newAdvisoriesInstantEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();

        public static native TemplateInstance newAdvisoriesInstantEmailTitleV2();

        public static native TemplateInstance newAdvisoriesInstantEmailBodyV2();

        public static native TemplateInstance dailyEmailTitleV2();

        public static native TemplateInstance dailyEmailBodyV2();
    }
}
