package com.redhat.cloud.notifications.auth.principal.turnpike;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * author hrupp
 */
public class TurnpikeSamlIdentity extends TurnpikeIdentity {

    /*
{
  "identity": {
    // The associate section contains the Associate type principal data
    "associate": {
      // The Roles correspond to LDAP groups
      "Role": [
        "some-ldap-group",
        "another-ldap-group"
      ],
      "email": "jschmoe@redhat.com",
      "givenName": "Joseph",
      "rhatUUID": "01234567-89ab-cdef-0123-456789abcdef",
      "surname": "Schmoe"
    },
    // In the future, Associates might be authenticated through other means
    "auth_type": "saml-auth",
    // The Associate type asserts that the request comes from an active Red Hat employee
    "type": "Associate"
  }
}

*/

    public Associate associate;

    @Override
    public String getName() {
        return associate.email;
    }

    public class Associate {
        public String email;
        public String givenName;
        public String surname;
        public String rhatUUID;

        @JsonProperty("Role")
        public String[] role;
    }

}
