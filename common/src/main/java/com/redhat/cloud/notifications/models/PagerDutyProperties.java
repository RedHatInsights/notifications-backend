package com.redhat.cloud.notifications.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.redhat.cloud.notifications.db.converters.HttpTypeConverter;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.redhat.cloud.notifications.Constants.PAGERDUTY_EVENT_V2_URL;

/**
 * The PagerDuty API uses a single endpoint and HTTP method for requests from all users, distinguished by the
 * Integration Key stored in the {@link PagerDutyProperties#secretToken}. This URL and method do not need to be provided
 * by end users, but are included in this Entity for future migrations.
 */
@Entity
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class) // TODO remove them once the transition to DTOs have been completed.
@Table(name = "pagerduty_properties")
// TODO add property for severity
// TODO integrate with everything else for PagerDuty
public class PagerDutyProperties extends EndpointProperties implements SourcesSecretable {
    @NotNull
    @ValidNonPrivateUrl
    private String url = PAGERDUTY_EVENT_V2_URL;

    // TODO remove
    @Convert(converter = HttpTypeConverter.class)
    @NotNull
    private HttpType method = HttpType.POST;

    // TODO remove
    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    @NotNull
    @Size(max = 255)
    @Transient
    private String secretToken;

    /**
     * The ID of the "secret token" secret in the Sources backend.
     */
    @Column(name = "secret_token_id")
    @JsonIgnore // TODO remove them once the transition to DTOs have been completed.
    private Long secretTokenSourcesId;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Boolean getDisableSslVerification() {
        return disableSslVerification;
    }

    public void setDisableSslVerification(Boolean disableSslVerification) {
        this.disableSslVerification = disableSslVerification;
    }

    public HttpType getMethod() {
        return method;
    }

    public void setMethod(HttpType method) {
        this.method = method;
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
