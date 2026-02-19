package com.redhat.cloud.notifications.connector.authentication.v2.sources;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SourcesSecretResult {

    public String username;
    public String password;
}
