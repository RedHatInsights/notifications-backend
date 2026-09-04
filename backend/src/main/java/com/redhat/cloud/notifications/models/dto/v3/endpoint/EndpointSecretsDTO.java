package com.redhat.cloud.notifications.models.dto.v3.endpoint;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

/**
 * Request payload used to create, update or delete an endpoint's secrets. See RHCLOUD-34316: unlike v1/v2, secrets
 * are never returned by the v3 API, so this DTO is only ever consumed, never produced.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public final class EndpointSecretsDTO {

    @Size(max = 255)
    private String secretToken;

    @Size(max = 255)
    private String bearerAuthentication;

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(final String secretToken) {
        this.secretToken = secretToken;
    }

    public String getBearerAuthentication() {
        return bearerAuthentication;
    }

    public void setBearerAuthentication(final String bearerAuthentication) {
        this.bearerAuthentication = bearerAuthentication;
    }
}
