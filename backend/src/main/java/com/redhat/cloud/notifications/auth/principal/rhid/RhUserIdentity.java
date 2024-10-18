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

        @JsonProperty("user_id")
        private String userId;

        public String getUsername() {
            return username;
        }

        public String getUserId() {
            return userId;
        }
    }

    @Override
    public String getName() {
        return getUser().getUsername();
    }

    @Override
    public String getUserId() {
        return getUser().getUserId();
    }

    @Override
    public String toString() {
        return "RhUserIdentity{" +
            "accountNumber='" + this.accountNumber + '\'' +
            ", orgId='" + this.orgId + '\'' +
            ", username='" + this.user.username + '\'' +
            ", userid='" + this.user.userId + '\'' +
            ", type='" + this.type + '\'' +
            '}';
    }
}
