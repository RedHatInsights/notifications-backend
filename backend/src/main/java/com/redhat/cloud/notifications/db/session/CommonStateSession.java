package com.redhat.cloud.notifications.db.session;

import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logging.Logger;

import javax.persistence.PrePersist;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

public interface CommonStateSession {

    public static CommonStateSession withSession(Mutiny.Session session) {
        return new SessionAdapter(session);
    }

    public static CommonStateSession withStatelessSession(Mutiny.StatelessSession statelessSession) {
        return new StatelessSessionAdapter(statelessSession);
    }

    <T> Uni<List<T>> find(Class<T> aClass, Object... ids);
    <T> Mutiny.Query<T> createQuery(String query, Class<T> aClass);
    Uni<Void> persist(Object object);
    Uni<Void> flush();
}

class SessionAdapter implements CommonStateSession {
    Mutiny.Session session;

    public SessionAdapter(Mutiny.Session session) {
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
    private static final Logger LOGGER = Logger.getLogger(StatelessSessionAdapter.class);
    private final Mutiny.StatelessSession statelessSession;

    public StatelessSessionAdapter(Mutiny.StatelessSession statelessSession) {
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
        for (Method method: object.getClass().getDeclaredMethods()) {
            PrePersist prePersist = method.getAnnotation(PrePersist.class);
            if (prePersist != null) {
                try {
                    method.invoke(object);
                } catch (InvocationTargetException | IllegalAccessException exception) {
                    LOGGER.warnf(exception, "Unable to call PrePersist method [%s] found in class [%s]", method.getName(), object.getClass().getName());
                }
            }
        }

        // Todo: Check if we need to retrieve the generated ID manually
        return statelessSession.insert(object);
    }

    @Override
    public Uni<Void> flush() {
        // Nothing to do
        return Uni.createFrom().voidItem();
    }
}
