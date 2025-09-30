package com.redhat.cloud.notifications.connector.email.model.aggregation;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Environment(
    @JsonProperty("url")        String url,
    @JsonProperty("ocm_url")    String ocmUrl
) { }
