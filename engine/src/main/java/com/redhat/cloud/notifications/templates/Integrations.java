package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import static com.redhat.cloud.notifications.events.FromCamelHistoryFiller.INTEGRATION_FAILED_EVENT_TYPE;
import static com.redhat.cloud.notifications.events.IntegrationDisabledNotifier.INTEGRATION_DISABLED_EVENT_TYPE;
import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;

// Name needs to be "Integrations" to read templates from resources/templates/Integrations
@ApplicationScoped
public class Integrations implements EmailTemplate {

    private static final String NO_TITLE_FOUND_MSG = "No email title template found for Integrations event_type: %s";
    private static final String NO_BODY_FOUND_MSG = "No email body template found for Integrations event_type: %s";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    public TemplateInstance getTitle(String eventType, EmailSubscriptionType type) {
        if (type == INSTANT) {
            switch (eventType) {
                case INTEGRATION_FAILED_EVENT_TYPE:
                    return getFailedIntegrationTitle();
                case INTEGRATION_DISABLED_EVENT_TYPE:
                    return getIntegrationDisabledTitle();
                default:
                    // Do nothing.
                    break;
            }
        }
        throw new UnsupportedOperationException(String.format(NO_TITLE_FOUND_MSG, eventType));
    }

    @Override
    public TemplateInstance getBody(String eventType, EmailSubscriptionType type) {
        if (type == INSTANT) {
            switch (eventType) {
                case INTEGRATION_FAILED_EVENT_TYPE:
                    return getFailedIntegrationBody();
                case INTEGRATION_DISABLED_EVENT_TYPE:
                    return getIntegrationDisabledBody();
                default:
                    // Do nothing.
                    break;
            }
        }
        throw new UnsupportedOperationException(String.format(NO_BODY_FOUND_MSG, eventType));
    }

    private TemplateInstance getFailedIntegrationBody() {
        if (featureFlipper.isIntegrationsEmailTemplatesV2Enabled()) {
            return Templates.failedIntegrationBodyV2();
        }
        return Templates.failedIntegrationBody();
    }

    private TemplateInstance getIntegrationDisabledBody() {
        if (featureFlipper.isIntegrationsEmailTemplatesV2Enabled()) {
            return Templates.integrationDisabledBodyV2();
        }
        return Templates.integrationDisabledBody();
    }

    private TemplateInstance getFailedIntegrationTitle() {
        if (featureFlipper.isIntegrationsEmailTemplatesV2Enabled()) {
            return Templates.failedIntegrationTitleV2();
        }
        return Templates.failedIntegrationTitle();
    }

    private TemplateInstance getIntegrationDisabledTitle() {
        if (featureFlipper.isIntegrationsEmailTemplatesV2Enabled()) {
            return Templates.integrationDisabledTitleV2();
        }
        return Templates.integrationDisabledTitle();
    }

    @Override
    public boolean isSupported(String eventType, EmailSubscriptionType type) {
        return type.equals(INSTANT);
    }

    @Override
    public boolean isEmailSubscriptionSupported(EmailSubscriptionType type) {
        return false;
    }

    @CheckedTemplate(requireTypeSafeExpressions = false)
    public static class Templates {

        public static native TemplateInstance failedIntegrationTitle();

        public static native TemplateInstance failedIntegrationBody();

        public static native TemplateInstance integrationDisabledTitle();

        public static native TemplateInstance integrationDisabledBody();

        public static native TemplateInstance failedIntegrationTitleV2();

        public static native TemplateInstance failedIntegrationBodyV2();

        public static native TemplateInstance integrationDisabledTitleV2();

        public static native TemplateInstance integrationDisabledBodyV2();
    }
}
