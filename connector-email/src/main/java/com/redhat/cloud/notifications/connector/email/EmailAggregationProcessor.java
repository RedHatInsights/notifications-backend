package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.DailyDigestSection;
import com.redhat.cloud.notifications.connector.email.model.EmailNotification;
import com.redhat.cloud.notifications.qute.templates.IntegrationType;
import com.redhat.cloud.notifications.qute.templates.TemplateDefinition;
import com.redhat.cloud.notifications.qute.templates.TemplateService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class EmailAggregationProcessor {

    @Inject
    TemplateService templateService;

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    public String aggregate(final EmailNotification emailNotification, final String orgId, String emailTitle) {
        Map<String, Object> environment = (Map<String, Object>) emailNotification.eventData().get("environment");
        String bundleName = emailNotification.eventData().get("bundle_name").toString();

        List<Map<String, Object>> listApplicationAggregatedData = (List<Map<String, Object>>) emailNotification.eventData().get("application_aggregated_data_list");

        Log.info("Starting aggregation for bundleName: " + bundleName);
        Map<String, DailyDigestSection> dataMap  = new HashMap<>();
        for (Map<String, Object> applicationAggregatedDataAsMap : listApplicationAggregatedData) {
            String appName =  (String) applicationAggregatedDataAsMap.get("appName");
            try {
                dataMap.put(appName, renderApplicationDailyDigestBody(bundleName, appName, (Map<String, Object>) applicationAggregatedDataAsMap.get("aggregatedData"), orgId, environment));
            } catch (Exception ex) {
                Log.error("Error rendering application template for " + appName, ex);
            }
        }

        if (!dataMap.isEmpty()) {
            // sort application by name
            List<DailyDigestSection> result = dataMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

            Map<String, Object> actionContext = new HashMap<>(Map.of("title", emailTitle, "items", result, "orgId", orgId));
            Map<String, Object> action = Map.of("context", actionContext, "bundle", bundleName);

            try {
                TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null);
                Map<String, Object> additionalContext = buildFullTemplateContext(action, environment);

                return templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
            } catch (Exception e) {
                Log.error(String.format("Error rendering daily digest email template for %s", bundleName), e);
                throw e;
            }
        }
        return null;
    }

    private DailyDigestSection renderApplicationDailyDigestBody(String bundle, String app, Map<String, Object> context, String orgId, final Map<String, Object> environment) {
        context.put("application", app);

        Map<String, Object> action =  Map.of("context", context, "bundle", bundle);

        DailyDigestSection builtSection = null;

        try {
            TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BODY, bundle, app, null, emailConnectorConfig.isUseBetaTemplatesEnabled(orgId, null));
            Map<String, Object> additionalContext = buildFullTemplateContext(action, environment);

            String renderedAppTemplate = templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
            builtSection = addItem(renderedAppTemplate);
        } catch (Exception e) {
            Log.error(String.format("Error rendering aggregated email template for %s/%s", bundle, app), e);
        }

        return builtSection;
    }

    private DailyDigestSection addItem(String template) {
        String titleData = template.split("<!-- Body section -->")[0];
        String bodyData = template.split("<!-- Body section -->")[1];
        String[] sections = titleData.split("<!-- next section -->");

        return new DailyDigestSection(bodyData, Arrays.stream(sections).filter(e -> !e.isBlank()).collect(Collectors.toList()));
    }

    private Map<String, Object> buildFullTemplateContext(final Map<String, Object> action, final Map<String, Object> environment) {
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", environment);
        additionalContext.put("ignore_user_preferences", false);
        additionalContext.put("action", action);
        additionalContext.put("pendo_message", null);
        return additionalContext;
    }
}
