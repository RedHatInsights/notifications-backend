package com.redhat.cloud.notifications.db;

import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.function.Consumer;
import java.util.function.Function;

@ApplicationScoped
public class StatelessSessionFactory {

    private static final Logger LOGGER = Logger.getLogger(StatelessSessionFactory.class);

    @Inject
    EntityManager entityManager;

    // An Hibernate session must never be shared across multiple threads.
    private final ThreadLocal<StatelessSession> threadLocalSession = new ThreadLocal<>();

    /**
     * This method opens a new stateless session, executes the given consumer using that session and then closes the
     * session.
     *
     * @param unitOfWork a unit of work
     * @throws IllegalStateException if a stateless session is already bound to the current thread
     */
    public void withSession(Consumer<StatelessSession> unitOfWork) {
        try {
            unitOfWork.accept(createSession());
        } finally {
            closeSession();
        }
    }

    /**
     * This method opens a new stateless session, executes the given function using that session and then closes the
     * session.
     *
     * @param unitOfWork a unit of work
     * @throws IllegalStateException if a stateless session is already bound to the current thread
     */
    public <T> T withSession(Function<StatelessSession, T> unitOfWork) {
        try {
            return unitOfWork.apply(createSession());
        } finally {
            closeSession();
        }
    }

    /**
     * Returns the current stateless session if it has been previously initialized with {@link #withSession(Consumer)}
     * or {@link #withSession(Function)}.
     *
     * @return the current stateless session
     * @throws IllegalStateException if the session has not been initialized before this method is called
     */
    public StatelessSession getCurrentSession() {
        StatelessSession session = threadLocalSession.get();
        if (session == null) {
            throw new IllegalStateException("Current stateless session not found. Did you use StatelessSessionFactory#withSession before calling this method?");
        } else {
            return session;
        }
    }

    private StatelessSession createSession() {
        if (threadLocalSession.get() != null) {
            throw new IllegalStateException("Stateless session already bound to the current thread. Did you use nested StatelessSessionFactory#withSession calls?");
        } else {
            LOGGER.trace("Creating a new stateless session");
            StatelessSession session = entityManager.unwrap(Session.class).getSessionFactory().openStatelessSession();
            /*
             * We're not using ThreadLocal#withInitial because StatelessSessionFactory#close needs to call ThreadLocal#get
             * without triggering a session creation.
             */
            threadLocalSession.set(session);
            return session;
        }
    }

    private void closeSession() {
        StatelessSession session = threadLocalSession.get();
        if (session != null) {
            if (session.isOpen()) {
                LOGGER.trace("Closing the current stateless session");
                session.close();
            }
            LOGGER.trace("Removing the current stateless session from ThreadLocal field");
            threadLocalSession.remove();
        }
    }
}
