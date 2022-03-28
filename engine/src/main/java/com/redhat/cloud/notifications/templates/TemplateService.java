package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.recipients.User;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateInstance;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class TemplateService {

    @Inject
    Engine engine;

    @Inject
    Environment environment;

    public TemplateInstance compileTemplate(String template, String name) {
        return engine.parse(template, null, name).instance();
    }

    public String renderTemplate(User user, Action action, TemplateInstance templateInstance) {
        return templateInstance
                .data("action", action)
                .data("user", user)
                .data("environment", environment)
                .render();
    }
}
