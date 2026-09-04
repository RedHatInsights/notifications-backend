package com.redhat.cloud.notifications.models.dto.v3.endpoint.properties;

import com.redhat.cloud.notifications.models.validation.ValidNonPrivateUrl;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public final class CamelPropertiesDTO extends EndpointPropertiesDTO {

    private Map<String, String> extras;

    @NotNull
    @ValidNonPrivateUrl
    private String url;

    public Map<String, String> getExtras() {
        return extras;
    }

    public void setExtras(final Map<String, String> extras) {
        this.extras = extras;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }
}
