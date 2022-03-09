package com.redhat.cloud.notifications.auth.principal.rhid;

import com.redhat.cloud.notifications.auth.principal.ConsolePrincipal;

public class RhIdPrincipal extends ConsolePrincipal<RhIdentity> {

    private final String account;

    public RhIdPrincipal(RhIdentity identity) {
        super(identity);
        this.account = identity.getAccountNumber();
    }

    public String getAccount() {
        return account;
    }
}
