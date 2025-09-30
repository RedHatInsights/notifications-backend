package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.config.EmailConnectorConfig;
import com.redhat.cloud.notifications.connector.email.model.EmailAggregation;
import com.redhat.cloud.notifications.connector.email.model.aggregation.AggregationAction;
import com.redhat.cloud.notifications.connector.email.model.aggregation.AggregationActionContext;
import com.redhat.cloud.notifications.connector.email.model.aggregation.ApplicationAggregatedData;
import com.redhat.cloud.notifications.connector.email.model.aggregation.DailyDigestSection;
import com.redhat.cloud.notifications.connector.email.model.aggregation.Environment;
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

    public String aggregate(final EmailAggregation emailAggregation, final String orgId, String emailTitle) {
        Log.info("Starting aggregation for bundleName: " + emailAggregation.bundleName());
        Map<String, DailyDigestSection> dataMap  = new HashMap<>();
        for (ApplicationAggregatedData applicationAggregatedData : emailAggregation.applicationAggregatedDataList()) {
            try {
                dataMap.put(
                    applicationAggregatedData.appName(),
                    renderApplicationDailyDigestBody(emailAggregation.bundleName(), applicationAggregatedData.appName(), applicationAggregatedData.aggregatedData(), orgId, emailAggregation.environment())
                );
            } catch (Exception ex) {
                Log.error("Error rendering application template for " + applicationAggregatedData.appName(), ex);
            }
        }

        if (!dataMap.isEmpty()) {
            // sort application by name
            List<DailyDigestSection> result = dataMap.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .toList();

            AggregationAction action = new AggregationAction(emailAggregation.bundleName(), new AggregationActionContext(emailTitle, result, orgId));

            try {
                TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY, null, null, null);

                Map<String, Object> additionalContext = buildFullTemplateContext(action, emailAggregation.environment());
                return templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
            } catch (Exception e) {
                Log.error(String.format("Error rendering daily digest email template for %s", emailAggregation.bundleName()), e);
                throw e;
            }
        }
        return null;
    }

    private DailyDigestSection renderApplicationDailyDigestBody(String bundle, String app, Map<String, Object> context, String orgId, final Environment environment) {
        context.put("application", app);
        Map<String, Object> action =  Map.of("context", context, "bundle", bundle);
        Map<String, Object> additionalContext = buildFullTemplateContext(action, environment);

        DailyDigestSection builtSection = null;

        try {
            TemplateDefinition templateDefinition = new TemplateDefinition(IntegrationType.EMAIL_DAILY_DIGEST_BODY, bundle, app, null, emailConnectorConfig.isUseBetaTemplatesEnabled(orgId, null));
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

    private Map<String, Object> buildFullTemplateContext(final Object action, final Environment environment) {
        Map<String, Object> additionalContext = new HashMap<>();
        additionalContext.put("environment", environment);
        additionalContext.put("ignore_user_preferences", false);
        additionalContext.put("action", action);
        additionalContext.put("pendo_message", null);
        return additionalContext;
    }
}
