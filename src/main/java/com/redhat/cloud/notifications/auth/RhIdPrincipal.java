package com.redhat.cloud.notifications.auth;

import java.security.Principal;

public class RhIdPrincipal implements Principal {

    private String name;
    private String account;

    public RhIdPrincipal(String name, String account) {
        this.name = name;
        this.account = account;
    }

    @Override
    public String getName() {
        return name;
    }

    public String getAccount() {
        return account;
    }
}
