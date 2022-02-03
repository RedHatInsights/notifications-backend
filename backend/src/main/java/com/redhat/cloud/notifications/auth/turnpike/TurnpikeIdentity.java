package com.redhat.cloud.notifications.auth.turnpike;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.redhat.cloud.notifications.auth.ConsoleDotIdentity;

public abstract class TurnpikeIdentity extends ConsoleDotIdentity {
    @JsonProperty
    public String auth_info;

}
