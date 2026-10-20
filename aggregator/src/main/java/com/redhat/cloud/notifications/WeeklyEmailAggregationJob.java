package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.DigestTriggerOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class WeeklyEmailAggregationJob extends AbstractEmailAggregationJob {

    public static final String EVENT_TYPE_NAME = "weekly-aggregation";

    @Inject
    DigestTriggerOrgConfigRepository digestTriggerOrgConfigRepository;

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
        digestTriggerOrgConfigRepository.createMissingDefaultWeeklyConfig(aggregatorConfig.getDefaultWeeklyDigestCron(), executionTime);
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
}
