package com.redhat.cloud.notifications.auth.principal;

import com.redhat.cloud.notifications.auth.principal.rhid.RhIdPrincipal;
import com.redhat.cloud.notifications.auth.principal.rhid.RhIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikeIdentity;
import com.redhat.cloud.notifications.auth.principal.turnpike.TurnpikePrincipal;

import static com.redhat.cloud.notifications.Constants.X_RH_IDENTITY_HEADER;

public class ConsolePrincipalFactory {

    private static final String MISSING_ORG_ID_MSG = "The org_id field is missing or blank in the " + X_RH_IDENTITY_HEADER + " header";

    public static ConsolePrincipal<?> fromIdentity(ConsoleIdentity identity) throws IllegalIdentityHeaderException {
        if (identity instanceof RhIdentity) {
            RhIdPrincipal principal = new RhIdPrincipal((RhIdentity) identity);
            if (principal.getOrgId() == null || principal.getOrgId().isBlank()) {
                throw new IllegalIdentityHeaderException(MISSING_ORG_ID_MSG);
            }
            return principal;
        } else if (identity instanceof TurnpikeIdentity) {
            return new TurnpikePrincipal((TurnpikeIdentity) identity);
        }

        throw new IllegalArgumentException(String.format("Unprocessed identity found. type: %s and name: %s", identity.type, identity.getName()));
    }

}
