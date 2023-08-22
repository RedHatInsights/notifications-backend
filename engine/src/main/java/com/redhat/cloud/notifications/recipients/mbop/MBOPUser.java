package com.redhat.cloud.notifications.recipients.mbop;

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
    @JsonProperty("email")          String email,
    @JsonProperty("first_name")     String firstName,
    @JsonProperty("last_name")      String lastName,
    @JsonProperty("is_active")      Boolean isActive,
    @JsonProperty("is_org_admin")   Boolean isOrgAdmin,
    @JsonProperty("is_internal")    Boolean isInternal,
    @JsonProperty("locale")         String locale
) {
}
