package com.redhat.cloud.notifications.auth.principal.turnpike;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * author hrupp
 */
public class TurnpikeSamlIdentity extends TurnpikeIdentity {

    // The associate section contains the Associate type principal data
    public Associate associate;

    @Override
    public String getName() {
        return associate.email;
    }

    public static class Associate {
        public String email;
        public String givenName;
        public String surname;
        public String rhatUUID;

        // The Roles correspond to LDAP groups
        @JsonProperty("Role")
        public String[] roles;
    }

}
