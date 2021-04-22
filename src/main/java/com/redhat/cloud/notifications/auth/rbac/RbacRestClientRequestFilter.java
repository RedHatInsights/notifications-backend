package com.redhat.cloud.notifications.auth.rbac;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;

/**
 * Filter to optionally add data on outgoing requests to the RBAC service.
 * This is meant for local development and not production.
 */
public class RbacRestClientRequestFilter implements ClientRequestFilter {

    private String authInfo;

    public RbacRestClientRequestFilter() {
        String tmp = System.getProperty("develop.exceptional.user.auth.info");
        if (tmp != null && !tmp.isEmpty()) {
            authInfo = Base64.getEncoder().encodeToString(tmp.getBytes());
        }
    }

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {

        if (authInfo != null) {
            URI uri = requestContext.getUri();
            if (uri.toString().startsWith("https://ci.cloud.redhat.com")) {
                requestContext.getHeaders().putSingle("Authorization", "Basic " + authInfo);
            }
        }
    }
}
