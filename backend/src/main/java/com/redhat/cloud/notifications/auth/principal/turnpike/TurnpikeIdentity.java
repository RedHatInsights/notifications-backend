package com.redhat.cloud.notifications.auth.principal.turnpike;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;

public abstract class TurnpikeIdentity extends ConsoleIdentity {
    @JsonProperty("auth_info")
    public String authInfo;

}
