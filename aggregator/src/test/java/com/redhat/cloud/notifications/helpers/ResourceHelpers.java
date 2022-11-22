package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.AggregationCronjobParameters;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.time.LocalDateTime;

@ApplicationScoped
public class ResourceHelpers {


    @Inject
    EntityManager entityManager;

    public void addEmailAggregation(String orgId, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(orgId, bundle, application, policyId, insightsId);
        addEmailAggregation(aggregation);
    }

    @Transactional
    public void addAggregationCronjobParameters(AggregationCronjobParameters aggregationCronjobParameters) {
        entityManager.persist(aggregationCronjobParameters);
    }

    public AggregationCronjobParameters findAggregationCronjobParametersByOrgId(String orgId) {
        return entityManager.createQuery("SELECT acp FROM AggregationCronjobParameters acp WHERE acp.orgId =:orgId", AggregationCronjobParameters.class) //
                .setParameter("orgId", orgId) //
                .getSingleResult();
    }

    @Transactional
    public void purgeAggregationCronjobParameters() {
        entityManager.createQuery("DELETE FROM AggregationCronjobParameters").executeUpdate();
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
