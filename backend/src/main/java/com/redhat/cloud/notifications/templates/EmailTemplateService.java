package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.smallrye.mutiny.Uni;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EmailTemplateService {

    @Inject
    Engine engine;

    public Uni<TemplateInstance> compileTemplate(String template, String name) {
        return Uni.createFrom().item(engine.parse(template, null, name))
                .onItem().transform(Template::instance);
    }

    public Uni<String> renderTemplate(User user, Action action, TemplateInstance templateInstance) {
        return templateInstance
                .data("action", action)
                .data("user", user)
                .createUni();
    }
}
