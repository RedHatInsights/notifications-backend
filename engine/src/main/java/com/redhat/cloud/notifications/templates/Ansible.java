package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

// Name needs to be "Ansible" to read templates from resources/templates/Ansible
@ApplicationScoped
public class Ansible implements EmailTemplate {

    protected static final String REPORT_AVAILABLE_EVENT = "report-available";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(REPORT_AVAILABLE_EVENT)) {
                return getInstantEmailTitle();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email title template for Ansible event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    private TemplateInstance getInstantEmailTitle() {
        if (featureFlipper.isAnsibleEmailTemplatesV2Enabled()) {
            return Ansible.Templates.instantEmailTitleV2();
        }
        return Ansible.Templates.instantEmailTitle();
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == EmailSubscriptionType.INSTANT) {
            if (eventType.equals(REPORT_AVAILABLE_EVENT)) {
                return getInstantEmailBody();
            }
        }

        throw new UnsupportedOperationException(String.format(
                "No email body template for Ansible event_type: %s and EmailSubscription: %s found.",
                eventType, type
        ));
    }

    private TemplateInstance getInstantEmailBody() {
        if (featureFlipper.isAnsibleEmailTemplatesV2Enabled()) {
            return Ansible.Templates.instantEmailBodyV2();
        }
        return Ansible.Templates.instantEmailBody();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return eventType.equals(REPORT_AVAILABLE_EVENT) && type == EmailSubscriptionType.INSTANT;
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return type == EmailSubscriptionType.INSTANT;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance instantEmailTitle();

        public static native TemplateInstance instantEmailBody();

        public static native TemplateInstance instantEmailTitleV2();

        public static native TemplateInstance instantEmailBodyV2();

    }

}
