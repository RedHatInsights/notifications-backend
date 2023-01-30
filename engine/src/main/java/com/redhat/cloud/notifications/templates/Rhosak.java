package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;

// Name needs to be "Rhosak" to read templates from resources/templates/Rhosak
@ApplicationScoped
public class Rhosak implements EmailTemplate {
    protected static final String SCHEDULED_UPGRADE = "scheduled-upgrade";
    protected static final String DISRUPTION = "disruption";
    protected static final String INSTANCE_CREATED = "instance-created";
    protected static final String INSTANCE_DELETED = "instance-deleted";
    protected static final String ACTION_REQUIRED = "action-required";
    private static final List<String> ALLOWED_EVENT_TYPES = Arrays.asList(SCHEDULED_UPGRADE, DISRUPTION, INSTANCE_DELETED, INSTANCE_CREATED, ACTION_REQUIRED);

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.DAILY) {
            return getDailyRhosakEmailsTitle();
        }
        if (eventType.equals(DISRUPTION)) {
            return getServiceDisruptionTitle();
        }
        if (eventType.equals(INSTANCE_CREATED)) {
            return getInstanceCreatedTitle();
        }
        if (eventType.equals(INSTANCE_DELETED)) {
            return getInstanceDeletedTitle();
        }
        if (eventType.equals(ACTION_REQUIRED)) {
            return getActionRequiredTitle();
        }
        if (eventType.equals(SCHEDULED_UPGRADE)) {
            return getScheduledUpgradeTitle();
        }
        throw new UnsupportedOperationException(String.format(
                "No email title template for RHOSAK event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.DAILY) {
            return getDailyRhosakEmailsBody();
        }
        if (eventType.equals(DISRUPTION)) {
            return getServiceDisruptionBody();
        }
        if (eventType.equals(INSTANCE_CREATED)) {
            return getInstanceCreatedBody();
        }
        if (eventType.equals(INSTANCE_DELETED)) {
            return getInstanceDeletedBody();
        }
        if (eventType.equals(ACTION_REQUIRED)) {
            return getActionRequiredBody();
        }
        if (eventType.equals(SCHEDULED_UPGRADE)) {
            return getScheduledUpgradeBody();
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for RHOSAK event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    private TemplateInstance getDailyRhosakEmailsBody() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.dailyRhosakEmailsBodyV2();
        }
        return Templates.dailyRhosakEmailsBody();
    }

    private TemplateInstance getServiceDisruptionBody() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.serviceDisruptionBodyV2();
        }
        return Templates.serviceDisruptionBody();
    }

    private TemplateInstance getInstanceCreatedBody() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.instanceCreatedBodyV2();
        }
        return Templates.instanceCreatedBody();
    }

    private TemplateInstance getInstanceDeletedBody() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.instanceDeletedBodyV2();
        }
        return Templates.instanceDeletedBody();
    }

    private TemplateInstance getActionRequiredBody() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.actionRequiredBodyV2();
        }
        return Templates.actionRequiredBody();
    }

    private TemplateInstance getScheduledUpgradeBody() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.scheduledUpgradeBodyV2();
        }
        return Templates.scheduledUpgradeBody();
    }

    private TemplateInstance getDailyRhosakEmailsTitle() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.dailyRhosakEmailsTitleV2();
        }
        return Templates.dailyRhosakEmailsTitle();
    }

    private TemplateInstance getServiceDisruptionTitle() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.serviceDisruptionTitleV2();
        }
        return Templates.serviceDisruptionTitle();
    }

    private TemplateInstance getInstanceCreatedTitle() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.instanceCreatedTitleV2();
        }
        return Templates.instanceCreatedTitle();
    }

    private TemplateInstance getInstanceDeletedTitle() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.instanceDeletedTitleV2();
        }
        return Templates.instanceDeletedTitle();
    }

    private TemplateInstance getActionRequiredTitle() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.actionRequiredTitleV2();
        }
        return Templates.actionRequiredTitle();
    }

    private TemplateInstance getScheduledUpgradeTitle() {
        if (featureFlipper.isRhosakEmailTemplatesV2Enabled()) {
            return Templates.scheduledUpgradeTitleV2();
        }
        return Templates.scheduledUpgradeTitle();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return ALLOWED_EVENT_TYPES.contains(eventType);
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return true;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance actionRequiredTitle();

        public static native TemplateInstance actionRequiredBody();

        public static native TemplateInstance instanceCreatedTitle();

        public static native TemplateInstance instanceCreatedBody();

        public static native TemplateInstance instanceDeletedTitle();

        public static native TemplateInstance instanceDeletedBody();

        public static native TemplateInstance dailyRhosakEmailsTitle();

        public static native TemplateInstance dailyRhosakEmailsBody();

        public static native TemplateInstance serviceDisruptionTitle();

        public static native TemplateInstance serviceDisruptionBody();

        public static native TemplateInstance scheduledUpgradeTitle();

        public static native TemplateInstance scheduledUpgradeBody();

        public static native TemplateInstance actionRequiredTitleV2();

        public static native TemplateInstance actionRequiredBodyV2();

        public static native TemplateInstance instanceCreatedTitleV2();

        public static native TemplateInstance instanceCreatedBodyV2();

        public static native TemplateInstance instanceDeletedTitleV2();

        public static native TemplateInstance instanceDeletedBodyV2();

        public static native TemplateInstance dailyRhosakEmailsTitleV2();

        public static native TemplateInstance dailyRhosakEmailsBodyV2();

        public static native TemplateInstance serviceDisruptionTitleV2();

        public static native TemplateInstance serviceDisruptionBodyV2();

        public static native TemplateInstance scheduledUpgradeTitleV2();

        public static native TemplateInstance scheduledUpgradeBodyV2();
    }
}
