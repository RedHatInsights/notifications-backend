package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.AggregationOrgConfig;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@ApplicationScoped
public class ResourceHelpers {


    @Inject
    EntityManager entityManager;

    public void addEmailAggregation(String orgId, String bundle, String application, String policyId, String insightsId) {
        addEmailAggregation(orgId, bundle, application, policyId, insightsId, LocalDateTime.now(ZoneOffset.UTC));
    }

    public void addEmailAggregation(String orgId, String bundle, String application, String policyId, String insightsId, LocalDateTime localDateTime) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(orgId, bundle, application, policyId, insightsId, localDateTime);
        addEmailAggregation(aggregation);
    }

    @Transactional
    public void addAggregationOrgConfig(AggregationOrgConfig aggregationOrgConfig) {
        entityManager.persist(aggregationOrgConfig);
    }

    public AggregationOrgConfig findAggregationOrgConfigByOrgId(String orgId) {
        entityManager.clear();
        return entityManager.createQuery("SELECT acp FROM AggregationOrgConfig acp WHERE acp.orgId =:orgId", AggregationOrgConfig.class)
                .setParameter("orgId", orgId)
                .getSingleResult();
    }

    @Transactional
    public void purgeAggregationOrgConfig() {
        entityManager.createQuery("DELETE FROM AggregationOrgConfig").executeUpdate();
        entityManager.clear();
    }


    @Transactional
    public void addEmailAggregation(EmailAggregation aggregation) {
        entityManager.persist(aggregation);
    }

    @Transactional
    public void purgeEmailAggregations() {
        entityManager.createQuery("DELETE FROM EmailAggregation").executeUpdate();
    }

    @Transactional
    public Integer purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE orgId = :orgId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        return entityManager.createQuery(query)
                .setParameter("orgId", key.getOrgId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate();
    }
}
