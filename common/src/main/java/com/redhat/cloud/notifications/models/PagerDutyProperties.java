package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PagerDuty uses a single REST endpoint for all users, distinguishing between them with an Integration Key
 * ({@link PagerDutyProperties#secretToken}) included in <em>the body of the POST request</em>, not the header.
 */
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class) // TODO remove them once the transition to DTOs have been completed.
@Table(name = "pagerduty_properties")
public class PagerDutyProperties extends EndpointProperties implements SourcesSecretable {

    @NotNull
    @Enumerated(EnumType.STRING)
    private PagerDutySeverity severity;

    @Size(max = 32)
    @Transient
    private String secretToken;

    /**
     * The ID of the "secret token" secret in the Sources backend.
     */
    @Column(name = "secret_token_id")
    @JsonIgnore // TODO remove them once the transition to DTOs have been completed.
    private Long secretTokenSourcesId;

    public PagerDutySeverity getSeverity() {
        return severity;
    }

    public void setSeverity(PagerDutySeverity severity) {
        this.severity = severity;
    }

    @Override
    public String getSecretToken() {
        return secretToken;
    }

    @Override
    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }

    @Override
    public Long getSecretTokenSourcesId() {
        return secretTokenSourcesId;
    }

    @Override
    public void setSecretTokenSourcesId(Long secretTokenSourcesId) {
        this.secretTokenSourcesId = secretTokenSourcesId;
    }

    @Override
    public BasicAuthentication getBasicAuthentication() {
        return null;
    }

    @Override
    public void setBasicAuthentication(BasicAuthentication basicAuthentication) {
        // Do nothing
    }

    @Override
    public Long getBasicAuthenticationSourcesId() {
        return null;
    }

    @Override
    public void setBasicAuthenticationSourcesId(Long basicAuthenticationSourcesId) {
        // do nothing
    }

    @Override
    public String getBearerAuthentication() {
        return null;
    }

    @Override
    public void setBearerAuthentication(String bearerAuthentication) {
        // do nothing
    }

    @Override
    public Long getBearerAuthenticationSourcesId() {
        return null;
    }

    @Override
    public void setBearerAuthenticationSourcesId(Long bearerAuthenticationSourcesId) {
        // do nothing
    }
}
