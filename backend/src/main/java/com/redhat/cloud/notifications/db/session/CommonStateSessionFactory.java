package com.redhat.cloud.notifications.db.session;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.function.Function;

@ApplicationScoped
public class CommonStateSessionFactory {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public <T> Uni<T> withSession(boolean useStatelessSession, Function<CommonStateSession, Uni<T>> function) {
        if (useStatelessSession) {
            return sessionFactory.withStatelessSession(statelessSession -> function.apply(CommonStateSession.withStatelessSession(statelessSession)));
        } else {
            return sessionFactory.withSession(session -> function.apply(CommonStateSession.withSession(session)));
        }
    }

}
