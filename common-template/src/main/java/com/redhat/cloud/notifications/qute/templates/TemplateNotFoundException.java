package com.redhat.cloud.notifications.qute.templates;

public class TemplateNotFoundException extends RuntimeException {

    public TemplateNotFoundException(final TemplateDefinition config) {
        super(buildErrorMessage(config));
    }

    private static String buildErrorMessage(final TemplateDefinition config) {
        return String.format(
            "No template definition found for %s-%s-%s-%s",
            config.integrationType().name().toLowerCase(),
            config.bundle(),
            config.application(),
            config.eventType());
    }
}
