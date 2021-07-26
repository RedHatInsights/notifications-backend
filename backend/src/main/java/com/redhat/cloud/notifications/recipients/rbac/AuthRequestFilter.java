package com.redhat.cloud.notifications.recipients.rbac;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

class AuthRequestFilter implements ClientRequestFilter {

    String authInfo;
    String secret;
    String application;

    AuthRequestFilter() {
        Config config = ConfigProvider.getConfig();
        secret = config.getOptionalValue("rbac.service-to-service.secret", String.class).orElse("addme");
        application = config.getOptionalValue("rbac.service-to-service.application", String.class).orElse("notifications");
        String tmp = System.getProperty("rbac.service-to-service.exceptional.auth.info");
        if (tmp != null && !tmp.isEmpty()) {
            authInfo = new String(Base64.getEncoder().encode(tmp.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        if (authInfo != null) {
            requestContext.getHeaders().remove("x-rh-rbac-account");
            requestContext.getHeaders().putSingle("Authorization", "Basic " + authInfo);
            return;
        }

        requestContext.getHeaders().putSingle("x-rh-rbac-psk", secret);
        requestContext.getHeaders().putSingle("x-rh-rbac-client-id", application);
    }
}
