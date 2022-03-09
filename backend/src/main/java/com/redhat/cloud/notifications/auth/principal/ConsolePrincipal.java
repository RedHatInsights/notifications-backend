package com.redhat.cloud.notifications.auth.principal;

import java.security.Principal;

public abstract class ConsolePrincipal<T extends ConsoleIdentity> implements Principal {
    private final String name;
    private final T identity;
    private static final ConsolePrincipal<ConsoleIdentity> NO_IDENTITY_PRINCIPAL = new ConsolePrincipal<>() { };

    private ConsolePrincipal() {
        this.identity = null;
        this.name = "-noauth-";
    }

    public ConsolePrincipal(T identity) {
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

    public static ConsolePrincipal<ConsoleIdentity> noIdentity() {
        return NO_IDENTITY_PRINCIPAL;
    }
}
