package com.redhat.cloud.notifications.models.dto.v1.endpoint.properties;

import com.redhat.cloud.notifications.models.HttpType;
import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import static com.redhat.cloud.notifications.Constants.PAGERDUTY_EVENT_V2_URL;

// TODO integrate with everything else for PagerDuty
/**
 * See {@link com.redhat.cloud.notifications.models.PagerDutyProperties} for default options
 */
public class PagerDutyPropertiesDTO extends EndpointPropertiesDTO {

    @NotNull
    @ValidNonPrivateUrl
    private String url = PAGERDUTY_EVENT_V2_URL;

    @NotNull
    private HttpType method = HttpType.POST;

    @NotNull
    private Boolean disableSslVerification = Boolean.FALSE;

    // TODO rename to integration_key?
    @Size(max = 255)
    @NotNull
    private String secretToken;

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
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

    public void setMethod(final HttpType method) {
        this.method = method;
    }

    public String getSecretToken() {
        return secretToken;
    }

    public void setSecretToken(final String secretToken) {
        this.secretToken = secretToken;
    }
}
