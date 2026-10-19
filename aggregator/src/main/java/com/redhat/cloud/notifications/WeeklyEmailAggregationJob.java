package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.DigestTriggerOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class WeeklyEmailAggregationJob extends AbstractEmailAggregationJob {

    public static final String EVENT_TYPE_NAME = "weekly-aggregation";

    @Inject
    DigestTriggerOrgConfigRepository digestTriggerOrgConfigRepository;

    @ConfigProperty(name = "notifications.default.weekly.digest.cron", defaultValue = "30 0 * * 1")
    String defaultWeeklyDigestCron;

    @Override
    protected String getJobType() {
        return "Weekly";
    }

    @Override
    protected String getEventTypeName() {
        return EVENT_TYPE_NAME;
    }

    @Override
    protected void createMissingDefaultConfigs(LocalDateTime executionTime) {
        digestTriggerOrgConfigRepository.createMissingDefaultWeeklyConfig(defaultWeeklyDigestCron, executionTime);
    }

    @Override
    protected List<AggregationCommand> getPendingAggregations(LocalDateTime executionTime) {
        return digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(executionTime);
    }

    @Override
    protected void updateOrgConfigs(List<String> orgIds, LocalDateTime executionTime) {
        digestTriggerOrgConfigRepository.updateLastAndNextRunForWeekly(orgIds, executionTime);
    }

    @Override
    protected String getPairsProcessedMetricName() {
        return "aggregator_job_orgid_application_pairs_processed_weekly";
    }

    @Override
    protected String getLastSuccessMetricName() {
        return "aggregator_job_last_success_weekly";
    }

    @Override
    protected String getPushGatewayJobName() {
        return "aggregator_job_weekly";
    }
}
