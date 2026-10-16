package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.db.DigestTriggerOrgConfigRepository;
import com.redhat.cloud.notifications.models.AggregationCommand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDateTime;
import java.util.List;

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class WeeklyEmailAggregationJob extends AbstractEmailAggregationJob {

    public static final String EVENT_TYPE_NAME = "weekly-aggregation";

    @Inject
    DigestTriggerOrgConfigRepository digestTriggerOrgConfigRepository;

    @ConfigProperty(name = "notifications.default.weekly.digest.cron", defaultValue = "0 0 * * MON")
    String defaultWeeklyDigestCron;

    public void processWeeklyEmail() {
        processEmail();
    }

    @Override
    protected String getJobType() {
        return "Weekly";
    }

    @Override
    protected String getEventTypeName() {
        return EVENT_TYPE_NAME;
    }

    @Override
    protected LocalDateTime computeExecutionTime() {
        // Use current time (no rounding needed for cron-based scheduling)
        return LocalDateTime.now(UTC);
    }

    @Override
    protected void createMissingDefaultConfigs() {
        digestTriggerOrgConfigRepository.createMissingDefaultWeeklyConfig(defaultWeeklyDigestCron);
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

    // For automatic tests purpose
    protected void setDefaultWeeklyDigestCron(String defaultWeeklyDigestCron) {
        this.defaultWeeklyDigestCron = defaultWeeklyDigestCron;
    }
}
