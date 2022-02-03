package com.redhat.cloud.notifications.auth.rhid;

import com.redhat.cloud.notifications.auth.ConsoleDotPrincipal;

public class RhIdPrincipal extends ConsoleDotPrincipal<RhIdentity> {

    private final String account;

    public RhIdPrincipal(RhIdentity identity) {
        super(identity);
        this.account = identity.getAccountNumber();
    }

    public String getAccount() {
        return account;
    }
}
