package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import org.hibernate.Session;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class ResourceHelpers {

    @Inject
    Session session;

    public void addEmailAggregation(String orgId, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(orgId, bundle, application, policyId, insightsId);
        addEmailAggregation(aggregation);
    }

    @Transactional
    public void addEmailAggregation(EmailAggregation aggregation) {
        session.persist(aggregation);
    }

    @Transactional
    public void purgeEmailAggregations() {
        session.createQuery("DELETE FROM EmailAggregation").executeUpdate();
    }

    @Transactional
    public Integer purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE orgId = :orgId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        return session.createQuery(query)
                .setParameter("orgId", key.getOrgId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate();
    }
}
