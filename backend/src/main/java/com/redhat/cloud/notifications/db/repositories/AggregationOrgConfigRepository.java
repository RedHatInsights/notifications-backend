package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.AggregationOrgConfig;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalTime;


@ApplicationScoped
public class AggregationOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void createOrUpdateDailyDigestPreference(String orgId, LocalTime expectedTime) {
        AggregationOrgConfig cronjobParameters = findJobAggregationOrgConfig(orgId);

        if (cronjobParameters != null) {
            cronjobParameters.setScheduledExecutionTime(expectedTime);
            entityManager.merge(cronjobParameters);
        } else {
            cronjobParameters = new AggregationOrgConfig(orgId, expectedTime);
            entityManager.persist(cronjobParameters);
        }
    }

    public AggregationOrgConfig findJobAggregationOrgConfig(String orgId) {
        return entityManager.find(AggregationOrgConfig.class, orgId);
    }
}
