package com.redhat.cloud.notifications.models.dto.v3.endpoint.properties;

import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.validation.constraints.NotNull;

public final class WebhookPropertiesDTO extends EndpointPropertiesDTO {

    @NotNull
    @ValidNonPrivateUrl
    private String url;

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }
}
