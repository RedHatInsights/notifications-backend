package com.redhat.cloud.notifications.connector.secrets;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Secret {

    public String username;
    public String password;
}
