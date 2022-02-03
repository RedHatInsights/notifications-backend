package com.redhat.cloud.notifications.auth.turnpike;

import com.redhat.cloud.notifications.auth.ConsoleDotPrincipal;

public class TurnpikePrincipal extends ConsoleDotPrincipal<TurnpikeIdentity> {
    public TurnpikePrincipal(TurnpikeIdentity identity) {
        super(identity);
    }
}
