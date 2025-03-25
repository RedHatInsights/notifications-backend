package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.DRAWER;
import static java.util.Map.entry;

public class DefaultTemplates {

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        // Default Drawer template
        entry(new TemplateDefinition(DRAWER, null, null, null), "Default/instantBody.md")
    );
}
