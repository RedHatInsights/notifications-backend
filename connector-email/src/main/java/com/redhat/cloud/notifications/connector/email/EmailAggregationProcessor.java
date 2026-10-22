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

import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_WEEKLY_DIGEST_BODY;
import static com.redhat.cloud.notifications.qute.templates.IntegrationType.EMAIL_WEEKLY_DIGEST_BUNDLE_AGGREGATION_BODY;

@ApplicationScoped
public class EmailAggregationProcessor {

    public static final String DAILY_DIGEST = "daily-digest";
    public static final String WEEKLY_DIGEST = "weekly-digest";
    public static final String SUBSCRIPTION_TYPE_WEEKLY = "WEEKLY";

    @Inject
    TemplateService templateService;

    @Inject
    EmailConnectorConfig emailConnectorConfig;

    public String aggregate(final EmailAggregation emailAggregation, final String orgId, String emailTitle, String subscriptionType) {
        Log.info("Starting aggregation for bundleName: " + emailAggregation.bundleName());
        Map<String, DailyDigestSection> dataMap  = new HashMap<>();
        for (ApplicationAggregatedData applicationAggregatedData : emailAggregation.applicationAggregatedDataList()) {
            try {
                dataMap.put(
                    applicationAggregatedData.appName(),
                    renderApplicationDigestBody(emailAggregation.bundleName(), applicationAggregatedData.appName(), applicationAggregatedData.aggregatedData(), orgId, emailAggregation.environment(), subscriptionType)
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
                // Bundle digest template don't have any specific application or event type, to avoid confusion in Unleash, we add "daily-digest" or "weekly-digest" as application and event type.
                // For backward compatibility: if subscriptionType is null (old messages in Kafka), it defaults to DAILY_DIGEST
                if (subscriptionType == null) {
                    Log.warnf("subscriptionType is null for bundle %s, defaulting to DAILY digest template (backward compatibility)", emailAggregation.bundleName());
                }
                String digestType = SUBSCRIPTION_TYPE_WEEKLY.equals(subscriptionType) ? WEEKLY_DIGEST : DAILY_DIGEST;
                boolean useBetaTemplate = emailConnectorConfig.isUseBetaTemplatesEnabled(orgId, emailAggregation.bundleName(), digestType, digestType);

                // For backward compatibility: if subscriptionType is null, use DAILY digest templates
                IntegrationType integrationType = SUBSCRIPTION_TYPE_WEEKLY.equals(subscriptionType)
                    ? EMAIL_WEEKLY_DIGEST_BUNDLE_AGGREGATION_BODY
                    : EMAIL_DAILY_DIGEST_BUNDLE_AGGREGATION_BODY;

                TemplateDefinition templateDefinition = new TemplateDefinition(integrationType, null, null, null, useBetaTemplate);

                Map<String, Object> additionalContext = buildFullTemplateContext(action, emailAggregation.environment());
                return templateService.renderTemplateWithCustomDataMap(templateDefinition, additionalContext);
            } catch (Exception e) {
                String subscriptionTypeStr = subscriptionType != null ? subscriptionType.toLowerCase() : "daily";
                Log.error(String.format("Error rendering %s digest email template for %s", subscriptionTypeStr, emailAggregation.bundleName()), e);
                throw e;
            }
        }
        return null;
    }

    private DailyDigestSection renderApplicationDigestBody(String bundle, String app, Map<String, Object> context, String orgId, final Environment environment, String subscriptionType) {
        context.put("application", app);
        Map<String, Object> action =  Map.of("context", context, "bundle", bundle);
        Map<String, Object> additionalContext = buildFullTemplateContext(action, environment);

        DailyDigestSection builtSection = null;

        try {
            // Application digest don't have any specific event type, to avoid confusion in Unleash we add "daily-digest" or "weekly-digest" as event type.
            // For backward compatibility: if subscriptionType is null (old messages in Kafka), it defaults to DAILY_DIGEST
            if (subscriptionType == null) {
                Log.warnf("subscriptionType is null for bundle %s / application %s, defaulting to DAILY digest template (backward compatibility)", bundle, app);
            }
            String digestType = SUBSCRIPTION_TYPE_WEEKLY.equals(subscriptionType) ? WEEKLY_DIGEST : DAILY_DIGEST;
            boolean useBetaTemplate = emailConnectorConfig.isUseBetaTemplatesEnabled(orgId, bundle, app, digestType);

            // For backward compatibility: if subscriptionType is null, use DAILY digest templates
            IntegrationType integrationType = SUBSCRIPTION_TYPE_WEEKLY.equals(subscriptionType)
                ? EMAIL_WEEKLY_DIGEST_BODY
                : EMAIL_DAILY_DIGEST_BODY;

            TemplateDefinition templateDefinition = new TemplateDefinition(integrationType, bundle, app, null, useBetaTemplate);
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
