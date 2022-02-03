package com.redhat.cloud.notifications.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsoleDotIdentityWrapper {
    private ConsoleDotIdentity identity;

    public ConsoleDotIdentity getIdentity() {
        return identity;
    }
}
