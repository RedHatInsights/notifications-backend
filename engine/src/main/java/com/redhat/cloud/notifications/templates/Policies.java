package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "Policies" to read templates from resources/templates/Policies
@ApplicationScoped
public class Policies implements EmailTemplate {

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return getInstantEmailTitle();
        }

        return getDailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return getInstantEmailBody();
        }

        return getDailyEmailBody();
    }

    private TemplateInstance getDailyEmailTitle() {
        if (featureFlipper.isPoliciesEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailTitleV2();
        }
        return Templates.dailyEmailTitle();
    }

    private TemplateInstance getDailyEmailBody() {
        if (featureFlipper.isPoliciesEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailBodyV2();
        }
        return Templates.dailyEmailBody();
    }

    private TemplateInstance getInstantEmailTitle() {
        if (featureFlipper.isPoliciesEmailTemplatesV2Enabled()) {
            return Templates.instantEmailTitleV2();
        }
        return Templates.instantEmailTitle();
    }

    private TemplateInstance getInstantEmailBody() {
        if (featureFlipper.isPoliciesEmailTemplatesV2Enabled()) {
            return Templates.instantEmailBodyV2();
        }
        return Templates.instantEmailBody();
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

        public static native TemplateInstance instantEmailTitle();

        public static native TemplateInstance instantEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();

        public static native TemplateInstance dailyEmailTitleV2();

        public static native TemplateInstance dailyEmailBodyV2();

        public static native TemplateInstance instantEmailTitleV2();

        public static native TemplateInstance instantEmailBodyV2();
    }

}
