package com.redhat.cloud.notifications.routers.dailydigest;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TriggerDailyDigestRequest {

    @NotBlank
    private final String applicationName;

    @NotBlank
    private final String bundleName;

    private final LocalDateTime end;

    @NotBlank
    private final String orgId;

    private final LocalDateTime start;

    @NotNull
    private final UUID bundleId;

    @NotNull
    private final UUID applicationId;

    /**
     * Creates a new instance of the class.
     * @param applicationName the application's name.
     * @param bundleName the bundle's name.
     * @param orgId the org ID that will be related to the aggregation.
     * @param start the start date to trigger the daily digest from. If a null
     *              value is given, then the date gets set to UTC "today", and
     *              the time gets set to UTC midnight.
     * @param end the end date to trigger the daily digest from. If a null
     *            value is given, then the date and time get set to UTC "now".
     */
    public TriggerDailyDigestRequest(
            final String applicationName,
            final String bundleName,
            final UUID bundleId,
            final UUID applicationId,
            final String orgId,
            final LocalDateTime start,
            final LocalDateTime end
    ) {
        this.applicationName = applicationName;
        this.bundleName = bundleName;
        this.bundleId = bundleId;
        this.applicationId = applicationId;
        this.orgId = orgId;

        this.start = Objects.requireNonNullElseGet(start, () -> {
            final LocalTime midnight = LocalTime.MIDNIGHT;
            final LocalDate today = LocalDate.now(ZoneOffset.UTC);

            return LocalDateTime.of(today, midnight);
        });

        this.end = Objects.requireNonNullElseGet(end, () -> LocalDateTime.now(ZoneOffset.UTC));
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getBundleName() {
        return bundleName;
    }

    public String getOrgId() {
        return orgId;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public UUID getBundleId() {
        return bundleId;
    }

    public UUID getApplicationId() {
        return applicationId;
    }
}
