package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CurrentStatus;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class StatusResources {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    public Uni<CurrentStatus> getCurrentStatus() {
        String query = "FROM CurrentStatus";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query, CurrentStatus.class)
                    .getSingleResult();
        });
    }

    public Uni<Void> setCurrentStatus(CurrentStatus currentStatus) {
        String query = "UPDATE CurrentStatus SET status = :status, startTime = :startTime, endTime = :endTime";
        return sessionFactory.withSession(session -> {
            return session.createQuery(query)
                    .setParameter("status", currentStatus.getStatus())
                    .setParameter("startTime", currentStatus.getStartTime())
                    .setParameter("endTime", currentStatus.getEndTime())
                    .executeUpdate()
                    .call(session::flush)
                    .replaceWith(Uni.createFrom().voidItem());
        });
    }
}
