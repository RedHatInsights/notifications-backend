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

    @Inject
    InvokerPrePersist invokerPrePersist;

    /**
     * Creates a stateless or stateful session depending on the provided params.
     *
     * It is useful in the cases when we need to support both session types in our code to avoid repeating the functions
     * or adding extra logic to handle each case by separate.
     *
     * As a rule of thumb, we would use a stateful session on rest calls and stateless on kafka messages
     *
     * <pre>
     * {
     * commonStateSessionFactory.withSession(stateless, session -> session.find(MyClass.class, "1"));
     * }
     * </pre>
     *
     * @param stateless if true, the session will be stateless, otherwise it will be stateful
     * @param function function to execute the session on
     * @return a Uni<T> with the result of the executed function
     */
    public <T> Uni<T> withSession(boolean stateless, Function<CommonStateSession, Uni<T>> function) {
        if (stateless) {
            return sessionFactory.withStatelessSession(statelessSession -> function.apply(new StatelessSessionAdapter(statelessSession, invokerPrePersist)));
        } else {
            return sessionFactory.withSession(session -> function.apply(new SessionAdapter(session)));
        }
    }

}
