package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.AggregationCronjobParameters;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@ApplicationScoped
public class AggregationCronjobParameterRepository {

    @Inject
    EntityManager entityManager;

    public List<AggregationCronjobParameters> getOrgIdToProceed(LocalDateTime now) {
        LocalTime currentHour = LocalTime.of(now.getHour(), 0, 0);
        String query = "SELECT acp FROM AggregationCronjobParameters acp WHERE acp.expectedRunningTime = :currentHour";

        return entityManager.createQuery(query, AggregationCronjobParameters.class)
                .setParameter("currentHour", currentHour)
                .getResultList();
    }
}
