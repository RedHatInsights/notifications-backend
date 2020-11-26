package com.redhat.cloud.notifications.templates;

import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.api.CheckedTemplate;


public class Policies {

    @CheckedTemplate
    public static class Templates {

        public static native TemplateInstance instantEmailTitle();

        public static native TemplateInstance instantEmailBody();
    }

}
