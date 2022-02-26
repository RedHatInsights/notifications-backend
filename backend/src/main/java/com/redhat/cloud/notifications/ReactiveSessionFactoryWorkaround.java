package com.redhat.cloud.notifications;

import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

// TODO NOTIF-488 Remove this class when notifications-common no longer depends on Hibernate Reactive.
public class ReactiveSessionFactoryWorkaround {

    @Produces
    @Singleton
    Mutiny.SessionFactory produce() {
        return null;
    }
}
