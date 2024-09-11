package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.redhat.cloud.notifications.Constants.PAGERDUTY_EVENT_V2_URL;

// TODO integrate with everything else for PagerDuty
public class PagerDutyPropertiesDTO extends EndpointPropertiesDTO {

    /**
     * See {@code url} field in {@link com.redhat.cloud.notifications.models.PagerDutyProperties}
     */
    @NotNull
    @ValidNonPrivateUrl
    private String url = PAGERDUTY_EVENT_V2_URL;

    @Size(max = 255)
    @NotNull
    private String secretToken;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(String secretToken) {
        this.secretToken = secretToken;
    }
}
