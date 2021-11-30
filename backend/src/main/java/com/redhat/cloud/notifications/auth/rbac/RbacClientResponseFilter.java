package com.redhat.cloud.notifications.auth.rbac;

import org.jboss.logging.Logger;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientResponseContext;
import javax.ws.rs.client.ClientResponseFilter;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * Filter to look at the response (code) from the Rbac server.
 * Log a warning if we have trouble reaching the server.
 */
public class RbacClientResponseFilter implements ClientResponseFilter {

    private static final Logger log = Logger.getLogger(RbacClientResponseFilter.class);

    @Override
    public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
        Response.StatusType statusInfo = responseContext.getStatusInfo();
        int status = statusInfo.getStatusCode();
        if (status != 200) {
            log.warnf("Call to the Rbac server failed with code %d, %s", status, statusInfo.getReasonPhrase());
        }
    }
}
