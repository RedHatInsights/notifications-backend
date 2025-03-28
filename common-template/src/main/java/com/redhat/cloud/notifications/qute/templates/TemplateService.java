package com.redhat.cloud.notifications.qute.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.mapping.Console;
import com.redhat.cloud.notifications.qute.templates.mapping.DefaultTemplates;
import com.redhat.cloud.notifications.qute.templates.mapping.OpenShift;
import com.redhat.cloud.notifications.qute.templates.mapping.Rhel;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateInstance;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class TemplateService {

    @Inject
    Engine engine;

    Map<TemplateDefinition, String> templatesConfigMap = new HashMap<>();

    @PostConstruct
    public void init() {
        templatesConfigMap.putAll(DefaultTemplates.templatesMap);
        templatesConfigMap.putAll(Console.templatesMap);
        templatesConfigMap.putAll(Rhel.templatesMap);
        templatesConfigMap.putAll(OpenShift.templatesMap);
    }

    public TemplateInstance compileTemplate(final TemplateDefinition config) {

        String path = templatesConfigMap.get(config);
        if (path == null) {
            path = templatesConfigMap.get(new TemplateDefinition(config.integrationType(), config.bundle(), config.application(), null));
        }
        if (path == null) {
            path = templatesConfigMap.get(new TemplateDefinition(config.integrationType(), null, null, null));
        }

        if (null != path) {
            return engine.getTemplate(config.integrationType().name().toLowerCase() + "/" + path).instance();
        }

        String notFoundErrorMessage = String.format(
            "No template definition found for %s-%s-%s-%s",
            config.integrationType().name().toLowerCase(),
            config.bundle(),
            config.application(),
            config.eventType());
        throw new RuntimeException(notFoundErrorMessage);
    }

    public String renderTemplate(final TemplateDefinition config, final Action action) {
        String result = compileTemplate(config)
            .data("data", action)
            .render();
        return result.trim();
    }

    public String renderTemplate(final TemplateDefinition config, final Map<String, Object> action) {
        String result = compileTemplate(config)
            .data("data", action)
            .render();
        return result.trim();
    }
}
