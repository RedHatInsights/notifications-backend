package com.redhat.cloud.notifications.qute.templates;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.mapping.Console;
import com.redhat.cloud.notifications.qute.templates.mapping.DefaultTemplates;
import com.redhat.cloud.notifications.qute.templates.mapping.OpenShift;
import com.redhat.cloud.notifications.qute.templates.mapping.Rhel;
import com.redhat.cloud.notifications.qute.templates.mapping.SubscriptionServices;
import io.quarkus.logging.Log;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Startup
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
        templatesConfigMap.putAll(SubscriptionServices.templatesMap);
        checkTemplatesConsistency();
    }

    /**
     * Check if declared template files exists and could be load by Qute
     */
    private void checkTemplatesConsistency() {
        ClassLoader classLoader = getClass().getClassLoader();
        for (TemplateDefinition templateDefinition : templatesConfigMap.keySet()) {
            String filePath = "templates/" + templateDefinition.integrationType().name().toLowerCase() + "/" + templatesConfigMap.get(templateDefinition);
            if (null == classLoader.getResource(filePath)) {
                Log.info("Template file " + filePath + " not found");
                throw new TemplateNotFoundException(templateDefinition);
            }
            engine.getTemplate(templateDefinition.integrationType().name().toLowerCase() + "/" + templatesConfigMap.get(templateDefinition)).instance();
        }
    }

    /**
     * This method will load the Qute Template Instance according Template Definition parameters.
     * If the template for the selected event type can't be found,
     * it will look for a generic template defined for the selected application,
     * it can't be found, it will look for a generic/system template defined for the selected integration type
     * @param config the template definition
     * @return the template instance
     *
     * @throws TemplateNotFoundException
     */
    private TemplateInstance compileTemplate(final TemplateDefinition config) throws TemplateNotFoundException {

        // try to find template path with full config parameters
        String path = templatesConfigMap.get(config);

        // if not found try to find if a default template for the app exists
        if (path == null) {
            path = templatesConfigMap.get(new TemplateDefinition(config.integrationType(), config.bundle(), config.application(), null));
            // if not found try to find if a default/system template for the integration type exists
            if (path == null) {
                path = templatesConfigMap.get(new TemplateDefinition(config.integrationType(), null, null, null));
                if (path == null) {
                    throw new TemplateNotFoundException(config);
                }
            }
        }

        // ask Qute to load the template instance from its file path, such as drawer/Policies/policyTriggeredBody.md
        return engine.getTemplate(config.integrationType().name().toLowerCase() + "/" + path).instance();
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
