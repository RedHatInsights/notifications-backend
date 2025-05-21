package com.redhat.cloud.notifications.qute.templates;

public record TemplateDefinition(IntegrationType integrationType, String bundle, String application, String eventType, boolean isBetaVersion) {
    public TemplateDefinition(IntegrationType integrationType, String bundle, String application, String eventType) {
        this(integrationType, bundle, application, eventType, false);
    }
}
