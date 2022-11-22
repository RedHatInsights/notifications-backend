package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCronjobParameters;
import org.hibernate.Session;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

@ApplicationScoped
public class AggregationCronjobParameterRepository {

    @Inject
    Session session;

    @Inject
    EntityManager entityManager;

    public List<AggregationCronjobParameters> getOrdIdToProceed(int currentHourAsInt) {
        LocalTime currentHour = LocalTime.of(LocalTime.now(ZoneOffset.UTC).getHour(), 0, 0);
        String query = "SELECT acp FROM AggregationCronjobParameters acp WHERE acp.expectedRunningTime = :currentHour";

        return session.createQuery(query, AggregationCronjobParameters.class)
                .setParameter("currentHour", currentHour)
                .getResultList();
    }
}
