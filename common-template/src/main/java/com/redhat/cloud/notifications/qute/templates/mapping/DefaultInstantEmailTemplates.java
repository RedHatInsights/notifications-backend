package com.redhat.cloud.notifications.qute.templates.mapping;


import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.*;
import static java.util.Map.entry;

public class DefaultInstantEmailTemplates {

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(
        entry(new TemplateDefinition(EMAIL_BODY, null, null, null), "Default/instantEmailBody.html")
    );
}
