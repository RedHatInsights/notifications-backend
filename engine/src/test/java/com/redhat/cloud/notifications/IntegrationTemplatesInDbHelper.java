package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.TemplateRepository;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.test.junit.mockito.InjectSpy;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class IntegrationTemplatesInDbHelper {

    protected static final String BUNDLE_RHEL = "rhel";

    @Inject
    protected ResourceHelpers resourceHelpers;

    @InjectSpy
    protected TemplateRepository templateRepository;

    protected final Map<String, UUID> eventTypes = new HashMap<>();

    Application application;

    @Inject
    TemplateService commonTemplateService;

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
    }

    @AfterEach
    protected void cleanUp() {
        resourceHelpers.cleanBundleAndApps();
    }

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
