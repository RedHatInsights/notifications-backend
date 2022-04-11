package com.redhat.cloud.notifications.auth.principal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ConsoleIdentityWrapper {
    private ConsoleIdentity identity;

    public ConsoleIdentity getIdentity() {
        return identity;
    }
}
