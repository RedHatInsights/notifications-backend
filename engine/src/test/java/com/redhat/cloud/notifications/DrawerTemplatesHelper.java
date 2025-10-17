package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import jakarta.inject.Inject;

import java.util.List;

public abstract class DrawerTemplatesHelper {

    protected static final String BUNDLE_RHEL = "rhel";

    @Inject
    TemplateService commonTemplateService;

    protected String generateDrawerTemplate(String eventTypeStr, Action action) {
        TemplateDefinition config = new TemplateDefinition(IntegrationType.DRAWER, getBundle(), getApp(), eventTypeStr);
        return commonTemplateService.renderTemplate(config, action);
    }

    protected String getBundle() {
        return BUNDLE_RHEL;
    }

    protected abstract String getApp();

    protected List<String> getUsedEventTypeNames() {
        return List.of();
    }
}
