package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.repositories.AggregationOrgConfigRepository;
import com.redhat.cloud.notifications.db.repositories.EmailAggregationRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class DailyEmailAggregationJob extends AbstractEmailAggregationJob {

    public static final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    EmailAggregationRepository emailAggregationRepository;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @Override
    protected String getJobType() {
        return "Daily";
    }

    @Override
    protected String getEventTypeName() {
        return EVENT_TYPE_NAME;
    }

    @Override
    protected void createMissingDefaultConfigs(LocalDateTime executionTime) {
        aggregationOrgConfigRepository.createMissingDefaultConfigurationBasedOnEvent(aggregatorConfig.getDefaultDailyDigestTime(), executionTime);
    }

    @Override
    protected List<AggregationCommand> getPendingAggregations(LocalDateTime executionTime) {
        return emailAggregationRepository.getApplicationsWithPendingAggregationAccordingOrgPref(executionTime);
    }

    @Override
    protected void updateOrgConfigs(List<String> orgIds, LocalDateTime executionTime) {
        aggregationOrgConfigRepository.updateLastCronJobRunAccordingOrgPref(orgIds, executionTime);
    }

    @Override
    protected void updateFailedOrgConfigs(List<String> orgIds, LocalDateTime executionTime) {
        aggregationOrgConfigRepository.updateLastRunForFailedDaily(orgIds, executionTime);
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
    protected String getDurationMetricName() {
        return "aggregator_job_duration_seconds";
    }
}
