package com.redhat.cloud.notifications.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RhIdentity {

    private Identity identity;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Identity {
        @JsonProperty("account_number")
        private String accountNumber;

        private User user;

        public String getAccountNumber() {
            return accountNumber;
        }

        public User getUser() {
            return user;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        @JsonProperty("username")
        private String username;

        public String getUsername() {
            return username;
        }
    }

    public Identity getIdentity() {
        return identity;
    }
}
