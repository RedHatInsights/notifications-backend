package com.redhat.cloud.notifications.qute.templates;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.qute.templates.mapping.AnsibleAutomationPlatform;
import com.redhat.cloud.notifications.qute.templates.mapping.Console;
import com.redhat.cloud.notifications.qute.templates.mapping.DefaultInstantEmailTemplates;
import com.redhat.cloud.notifications.qute.templates.mapping.DefaultTemplates;
import com.redhat.cloud.notifications.qute.templates.mapping.OpenShift;
import com.redhat.cloud.notifications.qute.templates.mapping.Rhel;
import com.redhat.cloud.notifications.qute.templates.mapping.SecureEmailTemplates;
import com.redhat.cloud.notifications.qute.templates.mapping.SubscriptionServices;
import io.quarkus.logging.Log;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

@Startup
@ApplicationScoped
public class TemplateService {

    private static final String SECURED_EMAIL_TEMPLATES = "notifications.use-secured-email-templates.enabled";
    private static final String DEFAULT_TEMPLATE = "notifications.use-default-template";

    // Only used in special environments.
    @ConfigProperty(name = SECURED_EMAIL_TEMPLATES, defaultValue = "false")
    boolean useSecuredEmailTemplates;

    // Only used in stage environments.
    @ConfigProperty(name = DEFAULT_TEMPLATE, defaultValue = "false")
    boolean defaultEmailTemplateEnabled;

    final Engine engine;

    final ObjectMapper objectMapper;

    Map<TemplateDefinition, String> templatesConfigMap = new HashMap<>();

    public TemplateService(Engine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    private String buildTemplateFilePath(TemplateDefinition templateDefinition, String templateFileName) {
        return templateDefinition.integrationType().getRootFolder()
            + File.separator
            + templateFileName;
    }

    @PostConstruct
    public void init() {
        templatesConfigMap.clear();
        if (isSecuredEmailTemplatesEnabled()) {
            templatesConfigMap.putAll(SecureEmailTemplates.templatesMap);
        } else {
            templatesConfigMap.putAll(DefaultTemplates.templatesMap);
            templatesConfigMap.putAll(AnsibleAutomationPlatform.templatesMap);
            templatesConfigMap.putAll(Console.templatesMap);
            templatesConfigMap.putAll(OpenShift.templatesMap);
            templatesConfigMap.putAll(Rhel.templatesMap);
            templatesConfigMap.putAll(SubscriptionServices.templatesMap);

            // For tenants onboarding, should be only enabled in stage
            if (isDefaultEmailTemplateEnabled()) {
                templatesConfigMap.putAll(DefaultInstantEmailTemplates.templatesMap);
            }
        }
        checkTemplatesConsistency();
    }

    public boolean isSecuredEmailTemplatesEnabled() {
        return useSecuredEmailTemplates;
    }

    public boolean isDefaultEmailTemplateEnabled() {
        return defaultEmailTemplateEnabled;
    }

    /**
     * Check if declared template files exists and could be load by Qute
     */
    private void checkTemplatesConsistency() {
        ClassLoader classLoader = getClass().getClassLoader();
        for (TemplateDefinition templateDefinition : templatesConfigMap.keySet()) {
            final String filePath = buildTemplateFilePath(templateDefinition, templatesConfigMap.get(templateDefinition));
            if (null == classLoader.getResource("templates/" + filePath)) {
                Log.info("Template file " + filePath + " not found");
                throw new TemplateNotFoundException(templateDefinition);
            }
            engine.getTemplate(filePath).instance();
        }
    }

    /**
     * This method will load the Qute Template Instance according Template Definition parameters.
     * If the template for the selected event type can't be found,
     * it will look for a generic template defined for the selected application,
     * it can't be found, it will look for a generic/system template defined for the selected integration type
     * @param originalTemplateDefinition the template definition
     * @return the template instance
     *
     * @throws TemplateNotFoundException
     */
    private TemplateInstance compileTemplate(final TemplateDefinition originalTemplateDefinition) throws TemplateNotFoundException {

        // try to find template path with full config parameters
        String path = templatesConfigMap.get(originalTemplateDefinition);

        TemplateDefinition templateDefinition = originalTemplateDefinition;

        // if not found try to find if a default template for the app exists
        if (path == null) {
            Log.debugf("No template found for %s", templateDefinition);
            templateDefinition = new TemplateDefinition(
                templateDefinition.integrationType(),
                templateDefinition.bundle(),
                templateDefinition.application(),
                null,
                templateDefinition.isBetaVersion());

            path = templatesConfigMap.get(templateDefinition);
            // if not found try to find if a default/system template for the integration type exists
            if (path == null) {
                Log.debugf("No template found for %s", templateDefinition);
                templateDefinition = new TemplateDefinition(
                    templateDefinition.integrationType(),
                    null,
                    null,
                    null,
                    templateDefinition.isBetaVersion());

                path = templatesConfigMap.get(templateDefinition);
                if (path == null) {
                    if (templateDefinition.isBetaVersion()) {
                        // if not found and templateDefinition is a beta version, try to find matching GA version.
                        Log.debugf("Beta template definition not found for %s, try to fallback on his GA version", originalTemplateDefinition);
                        TemplateDefinition templateGaVersion = new TemplateDefinition(
                            originalTemplateDefinition.integrationType(),
                            originalTemplateDefinition.bundle(),
                            originalTemplateDefinition.application(),
                            originalTemplateDefinition.eventType(),
                            false);
                        return compileTemplate(templateGaVersion);
                    }
                    throw new TemplateNotFoundException(templateDefinition);
                }
            }
        }
        final String filePath = buildTemplateFilePath(templateDefinition, templatesConfigMap.get(templateDefinition));
        // ask Qute to load the template instance from its file path, such as drawer/Policies/policyTriggeredBody.md
        return engine.getTemplate(filePath).instance();
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

    public String renderTemplateWithCustomDataMap(final TemplateDefinition config, final Map<String, Object> additionalContext) {
        TemplateInstance templateInstance = compileTemplate(config).data(additionalContext);
        String result = templateInstance.render();
        return result.trim();
    }

    public String getTemplateId(final TemplateDefinition config) {
        return compileTemplate(config).getTemplate().getId();
    }

    public Map<String, Object> convertActionToContextMap(final Action action) {
        return objectMapper
            .convertValue(action.getContext(), new TypeReference<Map<String, Object>>() { });
    }

    public boolean isValidTemplateDefinition(final TemplateDefinition config) {
        try {
            compileTemplate(config);
        } catch (TemplateNotFoundException e) {
            return false;
        }
        return true;
    }
}
