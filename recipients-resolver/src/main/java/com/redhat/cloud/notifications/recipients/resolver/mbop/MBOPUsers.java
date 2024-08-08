package com.redhat.cloud.notifications.recipients.resolver.mbop;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Represents the response to the {@link MBOPService#getUsersByOrgId(String, String, String, String, boolean, String, int, int)}
 * REST API call.
 */
public record MBOPUsers(
    List<MBOPUser> users,
    Long userCount
) {
    public record MBOPUser(
        @JsonProperty("id")             String id,
        @JsonProperty("username")       String username,
        @JsonProperty("email")          String email
    ) {
    }
}
