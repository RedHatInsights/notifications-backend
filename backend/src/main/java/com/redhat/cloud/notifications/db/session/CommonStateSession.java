package com.redhat.cloud.notifications.db.session;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import java.util.List;

public interface CommonStateSession {

    <T> Uni<List<T>> find(Class<T> aClass, Object... ids);
    <T> Mutiny.Query<T> createQuery(String query, Class<T> aClass);
    Uni<Void> persist(Object object);
    Uni<Void> flush();
}

class SessionAdapter implements CommonStateSession {
    Mutiny.Session session;

    SessionAdapter(Mutiny.Session session) {
        this.session = session;
    }

    @Override
    public <T> Uni<List<T>> find(Class<T> aClass, Object... ids) {
        return session.find(aClass, ids);
    }

    @Override
    public <T> Mutiny.Query<T> createQuery(String query, Class<T> aClass) {
        return session.createQuery(query, aClass);
    }

    @Override
    public Uni<Void> persist(Object object) {
        return session.persist(object);
    }

    @Override
    public Uni<Void> flush() {
        return session.flush();
    }
}

class StatelessSessionAdapter implements CommonStateSession {
    private final Mutiny.StatelessSession statelessSession;
    private final Invoker invoker = new Invoker();

    StatelessSessionAdapter(Mutiny.StatelessSession statelessSession) {
        this.statelessSession = statelessSession;
    }

    @Override
    public <T> Uni<List<T>> find(Class<T> aClass, Object... ids) {
        String query = "FROM " + aClass.getSimpleName() + " WHERE id IN (:ids)";
        return statelessSession.createQuery(query, aClass)
                .setParameter("ids", ids)
                .getResultList();
    }

    @Override
    public <T> Mutiny.Query<T> createQuery(String query, Class<T> aClass) {
        return statelessSession.createQuery(query, aClass);
    }

    @Override
    public Uni<Void> persist(Object object) {
        invoker.prePersist(object);

        // Todo: Check if we need to retrieve the generated ID manually
        return statelessSession.insert(object);
    }

    @Override
    public Uni<Void> flush() {
        // Nothing to do
        return Uni.createFrom().voidItem();
    }
}
