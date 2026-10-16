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

import static java.time.ZoneOffset.UTC;

@ApplicationScoped
public class DailyEmailAggregationJob extends AbstractEmailAggregationJob {

    public static final String EVENT_TYPE_NAME = "aggregation";

    @Inject
    EmailAggregationRepository emailAggregationResources;

    @Inject
    AggregationOrgConfigRepository aggregationOrgConfigRepository;

    @ConfigProperty(name = "notifications.default.daily.digest.time", defaultValue = "00:00")
    LocalTime defaultDailyDigestTime;

    public void processDailyEmail() {
        processEmail();
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
    protected LocalDateTime computeExecutionTime() {
        return computeScheduleExecutionTime();
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

    /**
     * Compute targeted schedule execution time local date time.
     * User can schedule execution time every 15 min.
     * Even if this job is executed at 05:02, we are looking for schedule executions of 05:00
     *
     * @return the local date time
     */
    public LocalDateTime computeScheduleExecutionTime() {
        final LocalDateTime currentTime = LocalDateTime.now(UTC).withSecond(0).withNano(0);

        // correct Time
        if (currentTime.getMinute() < 15) {
            return currentTime.withMinute(0);
        } else if (currentTime.getMinute() < 30) {
            return currentTime.withMinute(15);
        } else if (currentTime.getMinute() < 45) {
            return currentTime.withMinute(30);
        } else {
            return currentTime.withMinute(45);
        }
    }

    // For automatic tests purpose
    protected void setDefaultDailyDigestTime(LocalTime defaultDailyDigestTime) {
        this.defaultDailyDigestTime = defaultDailyDigestTime;
    }
}
