package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.models.DigestTriggerOrgConfig;
import com.redhat.cloud.notifications.models.DigestTriggerOrgConfigId;
import com.redhat.cloud.notifications.models.SubscriptionType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class DigestTriggerOrgConfigRepository {

    @Inject
    EntityManager entityManager;

    @Transactional
    public void createOrUpdateDigestPreference(String orgId, SubscriptionType subscriptionType, String cronExpression, LocalDateTime nextRun) {
        DigestTriggerOrgConfigId id = new DigestTriggerOrgConfigId(orgId, subscriptionType);
        DigestTriggerOrgConfig config = new DigestTriggerOrgConfig(id, cronExpression);
        config.setNextRun(nextRun);
        entityManager.merge(config);
    }

    public DigestTriggerOrgConfig findDigestTriggerOrgConfig(String orgId, SubscriptionType subscriptionType) {
        DigestTriggerOrgConfigId id = new DigestTriggerOrgConfigId(orgId, subscriptionType);
        return entityManager.find(DigestTriggerOrgConfig.class, id);
    }
}
