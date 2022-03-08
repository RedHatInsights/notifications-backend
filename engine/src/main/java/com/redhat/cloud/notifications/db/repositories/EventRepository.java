package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.Event;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public Event create(Event event) {
        event.prePersist(); // This method must be called manually while using a StatelessSession.
        statelessSessionFactory.getCurrentSession().insert(event);
        return event;
    }
}
