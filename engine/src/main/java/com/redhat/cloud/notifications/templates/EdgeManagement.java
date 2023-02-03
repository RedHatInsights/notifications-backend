package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "EdgeManagement" to read templates from resources/templates/Edge
@ApplicationScoped
public class EdgeManagement implements EmailTemplate {

    protected static final String IMAGE_CREATION = "image-creation";
    public static final String UPDATE_DEVICES = "update-devices";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (eventType.equals(IMAGE_CREATION)) {
            return getImageCreationTitle();
        } else if (eventType.equals(UPDATE_DEVICES)) {
            return getUpdateDeviceTitle();
        }
        throw new UnsupportedOperationException(String.format(
                "No email title template for Edge event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (eventType.equals(IMAGE_CREATION)) {
            return getImageCreationBody();
        } else if (eventType.equals(UPDATE_DEVICES)) {
            return getUpdateDeviceBody();
        }
        throw new UnsupportedOperationException(String.format(
                "No email body template for Edge event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    private TemplateInstance getImageCreationTitle() {
        if (featureFlipper.isEdgeManagementEmailTemplatesV2Enabled()) {
            return Templates.imageCreationTitleV2();
        }
        return Templates.imageCreationTitle();
    }

    private TemplateInstance getUpdateDeviceTitle() {
        if (featureFlipper.isEdgeManagementEmailTemplatesV2Enabled()) {
            return Templates.updateDeviceTitleV2();
        }
        return Templates.updateDeviceTitle();
    }

    private TemplateInstance getImageCreationBody() {
        if (featureFlipper.isEdgeManagementEmailTemplatesV2Enabled()) {
            return Templates.imageCreationBodyV2();
        }
        return Templates.imageCreationBody();
    }

    private TemplateInstance getUpdateDeviceBody() {
        if (featureFlipper.isEdgeManagementEmailTemplatesV2Enabled()) {
            return Templates.updateDeviceBodyV2();
        }
        return Templates.updateDeviceBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT && (eventType.equals(IMAGE_CREATION) || eventType.equals(UPDATE_DEVICES));
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance updateDeviceTitle();

        public static native TemplateInstance imageCreationTitle();

        public static native TemplateInstance updateDeviceBody();

        public static native TemplateInstance imageCreationBody();

        public static native TemplateInstance updateDeviceTitleV2();

        public static native TemplateInstance imageCreationTitleV2();

        public static native TemplateInstance updateDeviceBodyV2();

        public static native TemplateInstance imageCreationBodyV2();
    }

}
