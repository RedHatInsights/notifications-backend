package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.templates.DrawerTemplateMigrationService;
import com.redhat.cloud.notifications.templates.TemplateService;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.junit.jupiter.api.BeforeEach;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class IntegrationTemplatesInDbHelper {

    protected static final String BUNDLE_RHEL = "rhel";

    @Inject
    Environment environment;

    @Inject
    protected ResourceHelpers resourceHelpers;

    @InjectSpy
    protected TemplateRepository templateRepository;

    @Inject
    protected TemplateService templateService;


    @Inject
    DrawerTemplateMigrationService drawerTemplateMigrationService;

    protected final Map<String, UUID> eventTypes = new HashMap<>();

    Application application;

    @BeforeEach
    void initData() {
        Bundle bundle;
        try {
            bundle = resourceHelpers.findBundle(getBundle());
        } catch (NoResultException nre) {
            bundle = resourceHelpers.createBundle(getBundle());
        }

        try {
            application = resourceHelpers.findApp(getBundle(), getApp());
        } catch (NoResultException nre) {
            application = resourceHelpers.createApp(bundle.getId(), getApp());
        }
        for (String eventTypeToCreate : getUsedEventTypeNames()) {
            EventType eventType;
            try {
                eventType = resourceHelpers.findEventType(application.getId(), eventTypeToCreate);
            } catch (NoResultException nre) {
                eventType = resourceHelpers.createEventType(application.getId(), eventTypeToCreate);
            }
            eventTypes.put(eventTypeToCreate, eventType.getId());
        }
        migrate();
    }

    protected void migrate() {
        drawerTemplateMigrationService.migrate();
    }

    protected String generateDrawerTemplate(String eventTypeStr, Action action) {
        return generateIntegrationTemplate(getIntegrationType(), eventTypeStr, action);
    }

    protected String generateDrawerTemplate(String eventTypeStr, Map<String, Object> context) {
        return generateIntegrationTemplate("drawer", eventTypeStr, context);
    }

    protected String generateIntegrationTemplate(String integrationType, String eventTypeStr, Action action) {
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(application.getId(), eventTypes.get(eventTypeStr), null, IntegrationTemplate.TemplateKind.ALL, integrationType).get();
        TemplateInstance templateInstance = templateService.compileTemplate(integrationTemplate.getTheTemplate().getData(), integrationTemplate.getTheTemplate().getName());
        return renderTemplate(templateInstance, action);
    }

    protected String generateIntegrationTemplate(String integrationType, String eventTypeStr, Map<String, Object> context) {
        IntegrationTemplate integrationTemplate = templateRepository.findIntegrationTemplate(application.getId(), eventTypes.get(eventTypeStr), null, IntegrationTemplate.TemplateKind.ALL, integrationType).get();
        TemplateInstance templateInstance = templateService.compileTemplate(integrationTemplate.getTheTemplate().getData(), integrationTemplate.getTheTemplate().getName());
        return renderTemplate(templateInstance, context);
    }

    protected String renderTemplate(TemplateInstance template, Action action) {
        String result = template
            .data("data", action)
            .data("environment", environment)
            .render();

        return result.trim();
    }

    protected String renderTemplate(TemplateInstance templateInstance, Map<String, Object> context) {
        String result = templateInstance
            .data("action", Map.of("context", context, "bundle", getBundle(), "timestamp", LocalDateTime.now()))
            .data("environment", environment)
            .render();

        return result.trim();
    }

    protected String getBundle() {
        return BUNDLE_RHEL;
    }

    protected abstract String getApp();

    protected List<String> getUsedEventTypeNames() {
        return List.of();
    }

    protected String getIntegrationType() {
        return "drawer";
    }
}
