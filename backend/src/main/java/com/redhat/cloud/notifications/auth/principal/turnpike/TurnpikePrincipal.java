package com.redhat.cloud.notifications.auth.principal.turnpike;

import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;

public class TurnpikePrincipal extends ConsolePrincipal<TurnpikeIdentity> {
    public TurnpikePrincipal(TurnpikeIdentity identity) {
        super(identity);
    }
}
