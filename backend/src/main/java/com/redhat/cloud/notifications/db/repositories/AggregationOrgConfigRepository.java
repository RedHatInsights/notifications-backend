package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.AggregationOrgConfigId;
import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;


@ApplicationScoped
public class AggregationOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void createOrUpdateDailyDigestPreference(String orgId, LocalTime expectedTime) {
        AggregationOrgConfigId id = new AggregationOrgConfigId(orgId, SubscriptionType.DAILY);
        AggregationOrgConfig config = entityManager.find(AggregationOrgConfig.class, id);

        if (config != null) {
            config.setScheduledExecutionTime(expectedTime);
            entityManager.merge(config);
        } else {
            config = new AggregationOrgConfig(orgId, expectedTime);
            entityManager.persist(config);
        }
    }

    @Transactional
    public void createOrUpdateWeeklyDigestPreference(String orgId, LocalTime expectedTime, DayOfWeek preferredDay) {
        AggregationOrgConfigId id = new AggregationOrgConfigId(orgId, SubscriptionType.WEEKLY);
        AggregationOrgConfig config = entityManager.find(AggregationOrgConfig.class, id);

        if (config != null) {
            config.setScheduledExecutionTime(expectedTime);
            config.setPreferredDay(preferredDay);
            entityManager.merge(config);
        } else {
            config = new AggregationOrgConfig(orgId, SubscriptionType.WEEKLY, expectedTime, preferredDay);
            entityManager.persist(config);
        }
    }

    public AggregationOrgConfig findDailyDigestPreference(String orgId) {
        return entityManager.find(AggregationOrgConfig.class, new AggregationOrgConfigId(orgId, SubscriptionType.DAILY));
    }

    public AggregationOrgConfig findWeeklyDigestPreference(String orgId) {
        return entityManager.find(AggregationOrgConfig.class, new AggregationOrgConfigId(orgId, SubscriptionType.WEEKLY));
    }
}
