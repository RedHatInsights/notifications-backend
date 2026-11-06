package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.repositories.DigestTriggerOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.utils.DigestCronUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class WeeklyEmailAggregationJob extends AbstractEmailAggregationJob {

    public static final String EVENT_TYPE_NAME = "weekly-aggregation";

    @Inject
    DigestTriggerOrgConfigRepository digestTriggerOrgConfigRepository;

    @Inject
    DigestCronUtils digestCronUtils;

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
        digestTriggerOrgConfigRepository.createMissingDefaultWeeklyConfig(digestCronUtils.getDefaultWeeklyDigestCron());
    }

    @Override
    protected List<String> claimDueOrgs(LocalDateTime executionTime) {
        digestTriggerOrgConfigRepository.resetStaleClaims(executionTime, aggregatorConfig.getWeeklyStaleClaimThresholdHours());
        return digestTriggerOrgConfigRepository.claimDueWeeklyOrgs(executionTime);
    }

    @Override
    protected List<AggregationCommand> getPendingAggregations(LocalDateTime executionTime, List<String> claimedOrgIds) {
        return digestTriggerOrgConfigRepository.getApplicationsWithPendingWeeklyAggregation(executionTime, claimedOrgIds);
    }

    @Override
    protected void updateOrgConfigs(List<String> orgIds, LocalDateTime executionTime) {
        digestTriggerOrgConfigRepository.updateLastAndNextRunForWeekly(orgIds, executionTime);
    }

    @Override
    protected void updateFailedOrgConfigs(List<String> orgIds, LocalDateTime executionTime) {
        digestTriggerOrgConfigRepository.updateNextRunForFailedWeekly(orgIds, executionTime);
    }

    @Override
    protected void finalizeRun(List<String> allUpdatedOrgIds, List<String> claimedOrgIds, LocalDateTime executionTime) {
        // Advance orgs claimed this run that had no events (not in allUpdatedOrgIds)
        List<String> noEventsOrgIds = claimedOrgIds.stream()
            .filter(id -> !allUpdatedOrgIds.contains(id))
            .collect(Collectors.toList());
        digestTriggerOrgConfigRepository.advanceNextRunForOrgsWithNoEvents(executionTime, noEventsOrgIds);
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
    protected String getDurationMetricName() {
        return "aggregator_job_duration_seconds_weekly";
    }
}
