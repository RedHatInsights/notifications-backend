package com.redhat.cloud.notifications.auth.rhid;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.auth.ConsoleDotIdentity;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RhIdentity extends ConsoleDotIdentity {

    @JsonProperty("account_number")
    private String accountNumber;

    private User user;

    public String getAccountNumber() {
        return accountNumber;
    }

    public User getUser() {
        return user;
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
