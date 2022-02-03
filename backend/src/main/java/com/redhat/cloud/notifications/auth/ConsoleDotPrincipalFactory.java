package com.redhat.cloud.notifications.auth;

import com.redhat.cloud.notifications.auth.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.turnpike.TurnpikeIdentity;
import com.redhat.cloud.notifications.auth.turnpike.TurnpikePrincipal;

public class ConsoleDotPrincipalFactory {

    public static ConsoleDotPrincipal<?> fromIdentity(ConsoleDotIdentity identity) {
        if (identity instanceof RhIdentity) {
            return new RhIdPrincipal((RhIdentity) identity);
        } else if (identity instanceof TurnpikeIdentity) {
            return new TurnpikePrincipal((TurnpikeIdentity) identity);
        }

        throw new IllegalArgumentException("Unknown identity provided" + identity);
    }

}
