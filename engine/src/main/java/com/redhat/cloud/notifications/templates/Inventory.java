package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class Inventory implements EmailTemplate {

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return getValidationErrorEmailTitle();
        }
        return getDailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            return getValidationErrorEmailBody();
        }
        return getDailyEmailBody();
    }

    private TemplateInstance getValidationErrorEmailTitle() {
        if (featureFlipper.isInventoryEmailTemplatesV2Enabled()) {
            return Templates.validationErrorEmailTitleV2();
        }
        return Templates.validationErrorEmailTitle();
    }

    private TemplateInstance getDailyEmailTitle() {
        if (featureFlipper.isInventoryEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailTitleV2();
        }
        return Templates.dailyEmailTitle();
    }

    private TemplateInstance getValidationErrorEmailBody() {
        if (featureFlipper.isInventoryEmailTemplatesV2Enabled()) {
            return Templates.validationErrorEmailBodyV2();
        }
        return Templates.validationErrorEmailBody();
    }

    private TemplateInstance getDailyEmailBody() {
        if (featureFlipper.isInventoryEmailTemplatesV2Enabled()) {
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

        public static native TemplateInstance validationErrorEmailTitle();

        public static native TemplateInstance validationErrorEmailBody();

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();

        public static native TemplateInstance validationErrorEmailTitleV2();

        public static native TemplateInstance validationErrorEmailBodyV2();

        public static native TemplateInstance dailyEmailTitleV2();

        public static native TemplateInstance dailyEmailBodyV2();
    }

}
