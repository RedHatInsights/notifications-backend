package com.redhat.cloud.notifications;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.events.EventWrapperAction;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DrawerTemplatesHelper {

    protected static final String BUNDLE_RHEL = "rhel";

    @Inject
    TemplateService commonTemplateService;

    @Inject
    BaseTransformer baseTransformer;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    Environment environment;

    protected String generateDrawerTemplate(String eventTypeStr, Action action) {
        TemplateDefinition config = new TemplateDefinition(IntegrationType.DRAWER, getBundle(), getApp(), eventTypeStr);
        Event event = new Event();
        EventWrapperAction eventWrapperAction = new EventWrapperAction(action);
        event.setEventWrapper(eventWrapperAction);
        event.setBundleDisplayName(action.getBundle() + " display name");
        event.setApplicationDisplayName(action.getApplication() + " display name");
        event.setEventTypeDisplayName(action.getEventType() + " display name");
        JsonObject data = baseTransformer.toJsonObject(event);

        Map<String, Object> dataAsMap = new HashMap<>();
        try {
            dataAsMap.put("data", objectMapper.readValue(data.encode(), Map.class));
            dataAsMap.put("environment", JsonObject.mapFrom(environment));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Drawer notification data transformation failed", e);
        }

        return commonTemplateService.renderTemplateWithCustomDataMap(config, dataAsMap);
    }

    protected String getBundle() {
        return BUNDLE_RHEL;
    }

    protected abstract String getApp();

    protected List<String> getUsedEventTypeNames() {
        return List.of();
    }
}
