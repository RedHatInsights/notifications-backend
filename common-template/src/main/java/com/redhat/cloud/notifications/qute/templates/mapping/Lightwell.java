package com.redhat.cloud.notifications.qute.templates.mapping;

import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;

import java.util.Map;

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_TITLE;
import static java.util.Map.entry;

public class Lightwell {
    public static final String BUNDLE_NAME = "lightwell";

    public static final String LIGHTWELL_APP_NAME = "lightwell";
    static final String LIGHTWELL_FOLDER_NAME = "Lightwell/";

    public static final String LIGHTWELL_JAVA_REMEDIATED_EVENT_TYPE = "java-remediated";

    public static final Map<TemplateDefinition, String> templatesMap = Map.ofEntries(

        entry(new TemplateDefinition(EMAIL_BODY, BUNDLE_NAME, LIGHTWELL_APP_NAME, null), LIGHTWELL_FOLDER_NAME + "lightwellDefaultEmailBody.html"),
        entry(new TemplateDefinition(EMAIL_TITLE, BUNDLE_NAME, LIGHTWELL_APP_NAME, null), LIGHTWELL_FOLDER_NAME + "lightwellDefaultEmailTitle.txt")
    );
}
