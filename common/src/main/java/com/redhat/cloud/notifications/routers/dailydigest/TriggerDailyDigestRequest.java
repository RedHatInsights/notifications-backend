package com.redhat.cloud.notifications.routers.dailydigest;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

import java.beans.ConstructorProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TriggerDailyDigestRequest {

    @JsonProperty("application_name")
    @NotBlank
    private final String applicationName;

    @JsonProperty("bundle_name")
    @NotBlank
    private final String bundleName;

    @JsonProperty("end")
    private final LocalDateTime end;

    @JsonProperty("org_id")
    @NotBlank
    private final String orgId;

    @JsonProperty("start")
    private final LocalDateTime start;

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
    @ConstructorProperties({"application_name", "bundle_name", "org_id", "start", "end"})
    public TriggerDailyDigestRequest(
            final String applicationName,
            final String bundleName,
            final String orgId,
            final LocalDateTime start,
            final LocalDateTime end
    ) {
        this.applicationName = applicationName;
        this.bundleName = bundleName;
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
}
