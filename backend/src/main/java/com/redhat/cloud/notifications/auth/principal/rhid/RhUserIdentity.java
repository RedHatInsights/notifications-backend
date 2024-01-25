package com.redhat.cloud.notifications.auth.principal.rhid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RhUserIdentity extends RhIdentity {

    @JsonProperty("account_number")
    private String accountNumber;

    @JsonProperty("org_id")
    private String orgId;

    private User user;

    @Override
    public String getAccountNumber() {
        return accountNumber;
    }

    public User getUser() {
        return user;
    }

    @Override
    public String getOrgId() {
        return orgId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        @JsonProperty("username")
        private String username;

        public String getUsername() {
            return username;
        }
    }

    @Override
    public String getName() {
        return getUser().getUsername();
    }
}
