package com.redhat.cloud.notifications.connector.authentication.v2;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticationRequest {

    @JsonProperty("type")
    public AuthenticationType authenticationType;
    @JsonProperty("secretId")
    public Long secretId;
}
