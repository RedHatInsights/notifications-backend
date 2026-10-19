package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.EmailAggregationRepository;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.models.AggregationCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@ApplicationScoped
public class DailyEmailAggregationJob extends AbstractEmailAggregationJob {

    public static final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @ConfigProperty(name = "notifications.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDailyDigestTime;

    public void processDailyEmail(LocalDateTime executionTime) {
        processEmail(executionTime);
    }

    @Override
    protected String getJobType() {
        return "Daily";
    }

    @Override
    protected String getEventTypeName() {
        return EVENT_TYPE_NAME;
    }

    @Override
    protected void createMissingDefaultConfigs() {
        aggregationOrgConfigRepository.createMissingDefaultConfigurationBasedOnEvent(defaultDailyDigestTime);
    }

    @Override
    protected List<AggregationCommand> getPendingAggregations(LocalDateTime executionTime) {
        return emailAggregationResources.getApplicationsWithPendingAggregationAccordingOrgPref(executionTime);
    }

    @Override
    protected void updateOrgConfigs(List<String> orgIds, LocalDateTime executionTime) {
        aggregationOrgConfigRepository.updateLastCronJobRunAccordingOrgPref(orgIds, executionTime);
    }

    @Override
    protected String getPairsProcessedMetricName() {
        return "aggregator_job_orgid_application_pairs_processed";
    }

    @Override
    protected String getLastSuccessMetricName() {
        return "aggregator_job_last_success";
    }

    @Override
    protected String getPushGatewayJobName() {
        return "aggregator_job";
    }

    @Override
    protected Context buildContext() {
        return new Context.ContextBuilder()
            .withAdditionalProperty("single_daily_digest_enabled", true)
            .build();
    }

    // For automatic tests purpose
    protected void setDefaultDailyDigestTime(LocalTime defaultDailyDigestTime) {
        this.defaultDailyDigestTime = defaultDailyDigestTime;
    }
}
