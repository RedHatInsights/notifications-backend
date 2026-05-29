package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.BackendRestClient;
import com.redhat.cloud.notifications.mcp.McpPrincipal;
import com.redhat.cloud.notifications.mcp.McpToolUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class OrgConfigTools {

    @Inject
    SecurityIdentity securityIdentity;

    @Inject
    @RestClient
    BackendRestClient backendClient;

    @Inject
    MeterRegistry registry;

    @Tool(description = "Retrieves the daily digest time setting for the organization. Returns a UTC time string (e.g. \"09:00\") indicating when daily digest emails are sent.")
    public String getDailyDigestTimePreference() {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        return McpToolUtils.executeRestCall("getDailyDigestTimePreference", principal,
                () -> backendClient.getDailyDigestTimePreference(principal.getRawHeader()), registry);
    }
}
