package com.redhat.cloud.notifications.connector.authentication.v2;

import com.redhat.cloud.notifications.connector.authentication.v2.sources.SourcesSecretResponse;

public class AuthenticationResult {

    public String username;
    public String password;
    public AuthenticationType authenticationType;

    public AuthenticationResult(final SourcesSecretResponse secretResult, final AuthenticationType authenticationType) {
        this.username = secretResult.username;
        this.password = secretResult.password;
        this.authenticationType = authenticationType;
    }
}
