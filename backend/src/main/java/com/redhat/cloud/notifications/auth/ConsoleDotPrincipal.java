package com.redhat.cloud.notifications.auth;

import java.security.Principal;

public abstract class ConsoleDotPrincipal<T extends ConsoleDotIdentity> implements Principal {
    private final String name;
    private final T identity;
    private static final ConsoleDotPrincipal<ConsoleDotIdentity> NO_IDENTITY_PRINCIPAL = new ConsoleDotPrincipal<>() { };

    private ConsoleDotPrincipal() {
        this.identity = null;
        this.name = "-noauth-";
    }

    public ConsoleDotPrincipal(T identity) {
        this.identity = identity;
        this.name = identity.getName();
    }

    @Override
    public String getName() {
        return name;
    }

    public T getIdentity() {
        return this.identity;
    }

    public static ConsoleDotPrincipal<ConsoleDotIdentity> noIdentity() {
        return NO_IDENTITY_PRINCIPAL;
    }
}
