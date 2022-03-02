package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.session.StatelessSessionFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public Event create(Event event) {
        event.prePersist(); // This method must be called manually while using a StatelessSession.
        statelessSessionFactory.getOrCreateSession().insert(event);
        return event;
    }
}
