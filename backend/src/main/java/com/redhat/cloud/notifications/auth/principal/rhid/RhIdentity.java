package com.redhat.cloud.notifications.auth.principal.rhid;

import com.redhat.cloud.notifications.auth.principal.ConsoleIdentity;

public abstract class RhIdentity extends ConsoleIdentity {

    public abstract String getOrgId();

    public String getAccountNumber() {
        return null;
    }
}
