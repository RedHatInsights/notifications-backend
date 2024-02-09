package com.redhat.cloud.notifications.connector.authentication.secrets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SourcesSecret {

    public String username;
    public String password;
}
