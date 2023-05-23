package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.DrawerNotification;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class DrawerNotificationRepository {
    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public DrawerNotification create(DrawerNotification drawerNotification) {
        drawerNotification.prePersist(); // This method must be called manually while using a StatelessSession.
        statelessSessionFactory.getCurrentSession().insert(drawerNotification);
        return drawerNotification;
    }

}
