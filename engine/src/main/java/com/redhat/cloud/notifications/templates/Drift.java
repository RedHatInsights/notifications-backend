package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "Drift" to read templates from resources/templates/Drift
@ApplicationScoped
public class Drift implements EmailTemplate {

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return getNewBaselineDriftInstantEmailTitle();
        }

        return getDailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return getNewBaselineDriftInstantEmailBody();
        }

        return getDailyEmailBody();
    }

    private TemplateInstance getNewBaselineDriftInstantEmailTitle() {
        if (featureFlipper.isDriftEmailTemplatesV2Enabled()) {
            return Templates.newBaselineDriftInstantEmailTitleV2();
        }
        return Templates.newBaselineDriftInstantEmailTitle();
    }

    private TemplateInstance getDailyEmailTitle() {
        if (featureFlipper.isDriftEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailTitleV2();
        }
        return Templates.dailyEmailTitle();
    }

    private TemplateInstance getNewBaselineDriftInstantEmailBody() {
        if (featureFlipper.isDriftEmailTemplatesV2Enabled()) {
            return Templates.newBaselineDriftInstantEmailBodyV2();
        }
        return Templates.newBaselineDriftInstantEmailBody();
    }

    private TemplateInstance getDailyEmailBody() {
        if (featureFlipper.isDriftEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailBodyV2();
        }
        return Templates.dailyEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return true;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance newBaselineDriftInstantEmailTitle();

        public static native TemplateInstance newBaselineDriftInstantEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();

        public static native TemplateInstance newBaselineDriftInstantEmailTitleV2();

        public static native TemplateInstance newBaselineDriftInstantEmailBodyV2();

        public static native TemplateInstance dailyEmailTitleV2();

        public static native TemplateInstance dailyEmailBodyV2();

    }

}
