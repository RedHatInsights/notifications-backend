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

    String emailTemplatePrefix = "Secure/";
    Map<TemplateDefinition, String> templatesConfigMap = new HashMap<>();

    public TemplateService(Engine engine, ObjectMapper objectMapper) {
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    public String getTemplatePrefix(final IntegrationType integrationType) {
        if (isSecuredEmailTemplatesEnabled() &&
            integrationType.getRootFolder().equals(IntegrationType.EMAIL_BODY.getRootFolder())) {
            return emailTemplatePrefix;
        } else {
            return "";
        }
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
            String filePath = templateDefinition.integrationType().getRootFolder() + File.separator + getTemplatePrefix(templateDefinition.integrationType()) + templatesConfigMap.get(templateDefinition);
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
     * @param templateDefinition the template definition
     * @return the template instance
     *
     * @throws TemplateNotFoundException
     */
    private TemplateInstance compileTemplate(final TemplateDefinition templateDefinition) throws TemplateNotFoundException {

        // try to find template path with full config parameters
        String path = templatesConfigMap.get(templateDefinition);

        // if not found try to find if a default template for the app exists
        if (path == null) {
            path = templatesConfigMap.get(new TemplateDefinition(templateDefinition.integrationType(), templateDefinition.bundle(), templateDefinition.application(), null));
            // if not found try to find if a default/system template for the integration type exists
            if (path == null) {
                path = templatesConfigMap.get(new TemplateDefinition(templateDefinition.integrationType(), null, null, null));
                if (path == null) {
                    throw new TemplateNotFoundException(templateDefinition);
                }
            }
        }

        // ask Qute to load the template instance from its file path, such as drawer/Policies/policyTriggeredBody.md
        return engine.getTemplate(templateDefinition.integrationType().getRootFolder() + File.separator + getTemplatePrefix(templateDefinition.integrationType()) + path).instance();
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
}
