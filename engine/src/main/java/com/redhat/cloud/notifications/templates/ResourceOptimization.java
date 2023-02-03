package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "ResourceOptimization" to read templates from resources/templates/ResourceOptimization
@ApplicationScoped
public class ResourceOptimization implements EmailTemplate {

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        return getDailyEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        return getDailyEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return type == EmailSubscriptionType.DAILY;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.DAILY;
    }

    private TemplateInstance getDailyEmailTitle() {
        if (featureFlipper.isResourceOptimizationManagementEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailTitleV2();
        }
        return Templates.dailyEmailTitle();
    }

    private TemplateInstance getDailyEmailBody() {
        if (featureFlipper.isResourceOptimizationManagementEmailTemplatesV2Enabled()) {
            return Templates.dailyEmailBodyV2();
        }
        return Templates.dailyEmailBody();
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance dailyEmailTitle();

        public static native TemplateInstance dailyEmailBody();

        public static native TemplateInstance dailyEmailTitleV2();

        public static native TemplateInstance dailyEmailBodyV2();
    }

}
