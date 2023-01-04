package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.recipients.User;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.scheduler.Scheduled;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class TemplateService {

    @Inject
    Engine engine;

    @Inject
    Environment environment;

    @Inject
    FeatureFlipper featureFlipper;

    /*
     * When a DB template is modified (edited or deleted), its old version may still be included into another template
     * because the Qute engine has an internal cache. This scheduled method clears that cache periodically. We may want
     * to replace this with a better solution based on a Kafka topic and message broadcasting to all engine pods later.
     */
    @Scheduled(every = "${notifications.template-service.scheduled-clear.period:5m}", delayed = "${notifications.template-service.scheduled-clear.initial-delay:5m}")
    public void clearTemplates() {
        if (featureFlipper.isUseTemplatesFromDb()) {
            engine.clearTemplates();
        }
    }

    public TemplateInstance compileTemplate(String template, String name) {
        return engine.parse(template, null, name).instance();
    }

    public String renderTemplate(User user, Object data, TemplateInstance templateInstance) {
        return templateInstance
                .data("data", data)
                .data("user", user)
                .data("environment", environment)
                .render();
    }
}
