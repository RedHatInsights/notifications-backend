package com.redhat.cloud.notifications.auth.principal;

import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikeIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikePrincipal;

public class ConsolePrincipalFactory {

    public static ConsolePrincipal<?> fromIdentity(ConsoleIdentity identity) {
        if (identity instanceof RhIdentity) {
            return new RhIdPrincipal((RhIdentity) identity);
        } else if (identity instanceof TurnpikeIdentity) {
            return new TurnpikePrincipal((TurnpikeIdentity) identity);
        }

        throw new IllegalArgumentException(String.format("Unprocessed identity found. type: %s and name: %s", identity.type, identity.getName()));
    }

}
