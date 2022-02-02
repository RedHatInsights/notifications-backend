/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.cloud.notifications.auth.turnpike;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author hrupp
 */
public class SamlIdentity extends TurnpikeIdentity {

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
    public String getSubject() {
        return associate.email;
    }

    public class Associate {
        public String email;
        public String givenName;
        public String surname;
        public String rhatUUID;

        @JsonProperty("Role")
        String[] role;
    }

}
