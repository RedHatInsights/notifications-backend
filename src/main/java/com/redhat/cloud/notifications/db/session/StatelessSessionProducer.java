package com.redhat.cloud.notifications.db.session;

import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.RequestScoped;
import javax.enterprise.inject.Disposes;
import javax.inject.Inject;
import javax.ws.rs.Produces;

public class StatelessSessionProducer {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Produces
    @RequestScoped
    public Mutiny.StatelessSession produceStatelessSession() {
        return sessionFactory.openStatelessSession();
    }

    public void disposeStatelessSession(@Disposes Mutiny.StatelessSession session) {
        if (session != null) {
            session.close();
        }
    }
}
