package com.redhat.cloud.notifications.recipients.resolver.mbop;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents the response to the {@link MBOPService#getUsersByOrgId(String, String, String, String, boolean, String, int, int)}
 * REST API call. The {@link JsonProperty} annotations are required because
 * otherwise Jackson struggles to deserialize the incoming payload into a
 * record structure.
 */
public record MBOPUser(
    @JsonProperty("id")             String id,
    @JsonProperty("username")       String username,
    @JsonProperty("email")          String email
) {
}
