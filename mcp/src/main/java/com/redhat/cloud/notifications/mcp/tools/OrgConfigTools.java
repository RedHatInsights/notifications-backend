package com.redhat.cloud.notifications.mcp.tools;

import com.redhat.cloud.notifications.mcp.BackendRestClient;
import com.redhat.cloud.notifications.mcp.McpPrincipal;
import com.redhat.cloud.notifications.mcp.McpToolUtils;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalTime;

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

    @Tool(description = "Sets the daily digest time for the organization. The time must be in HH:mm format (UTC), and the minute value must be 00, 15, 30, or 45. Example: \"09:00\" or \"14:30\"")
    public String setDailyDigestTimePreference(
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):(00|15|30|45)$") @ToolArg(description = "The UTC time in HH:mm format (e.g., \"09:00\"). Minute values must be 00, 15, 30, or 45.") String time) {
        McpPrincipal principal = (McpPrincipal) securityIdentity.getPrincipal();
        LocalTime localTime = LocalTime.parse(time);
        McpToolUtils.executeRestCall("setDailyDigestTimePreference", principal,
                () -> {
                    backendClient.setDailyDigestTimePreference(principal.getRawHeader(), localTime);
                    return null;
                }, registry);
        return "Daily digest time preference set to " + time + " UTC";
    }
}
