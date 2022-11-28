package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.AggregationCronjobParameters;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalTime;


@ApplicationScoped
public class AggregationCronjobParametersRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void createOrUpdateDailyDigestPreference(String orgId, LocalTime expectedTime) {
        AggregationCronjobParameters cronjobParameters = findJobAggregationCronjobParameters(orgId);

        if (cronjobParameters != null) {
            cronjobParameters.setExpectedRunningTime(expectedTime);
            entityManager.merge(cronjobParameters);
        } else {
            cronjobParameters = new AggregationCronjobParameters(orgId, expectedTime);
            entityManager.persist(cronjobParameters);
        }
    }

    public AggregationCronjobParameters findJobAggregationCronjobParameters(String orgId) {
        return entityManager.find(AggregationCronjobParameters.class, orgId);
    }
}
