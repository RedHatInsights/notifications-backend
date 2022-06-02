package com.redhat.cloud.notifications.auth.principal.rhid;

import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;

public class RhIdPrincipal extends ConsolePrincipal<RhIdentity> {

    public RhIdPrincipal(RhIdentity identity) {
        super(identity);
    }

    public String getAccount() {
        return getIdentity().getAccountNumber();
    }

    public String getOrgId() {
        return getIdentity().getOrgId();
    }
}
