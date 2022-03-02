package com.redhat.cloud.notifications.session;

import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.function.Consumer;

@ApplicationScoped
public class StatelessSessionFactory {

    private static final Logger LOGGER = Logger.getLogger(StatelessSessionFactory.class);

    @Inject
    EntityManager entityManager;

    // An Hibernate session must never be shared across multiple threads.
    private final ThreadLocal<StatelessSession> threadLocalSession = new ThreadLocal<>();

    /**
     * This method opens a new stateless session or reuses an existing one if available. That session is then passed to
     * the given consumer which is executed. At the end of the execution, the session is closed.
     * @param unitOfWork a unit of work
     */
    public void withSession(Consumer<StatelessSession> unitOfWork) {
        try {
            unitOfWork.accept(getOrCreateSession());
        } finally {
            closeSession();
        }
    }

    public StatelessSession getOrCreateSession() {
        final StatelessSession currentSession = threadLocalSession.get();
        if (currentSession == null) {
            LOGGER.trace("Creating a new stateless session");
            StatelessSession newSession = entityManager.unwrap(Session.class).getSessionFactory().openStatelessSession();
            /*
             * We're not using ThreadLocal#withInitial because StatelessSessionFactory#close needs to call ThreadLocal#get
             * without triggering a session creation.
             */
            threadLocalSession.set(newSession);
            return newSession;
        } else {
            LOGGER.trace("Using an existing stateless session");
            return currentSession;
        }
    }

    public void closeSession() {
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
