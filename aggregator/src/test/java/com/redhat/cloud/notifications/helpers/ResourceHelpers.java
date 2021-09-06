package com.redhat.cloud.notifications.helpers;

import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import org.hibernate.Session;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import java.time.LocalDateTime;

@ApplicationScoped
public class ResourceHelpers {

    private static final Logger LOGGER = Logger.getLogger(ResourceHelpers.class);

    @Inject
    Session session;

    public void addEmailAggregation(String tenant, String bundle, String application, String policyId, String insightsId) {
        EmailAggregation aggregation = TestHelpers.createEmailAggregation(tenant, bundle, application, policyId, insightsId);
        addEmailAggregation(aggregation);
    }

    @Transactional
    public Boolean addEmailAggregation(EmailAggregation aggregation) {
        try {
            session.persist(aggregation);
            session.flush();
            return Boolean.TRUE;
        } catch (Exception e) {
            LOGGER.warn("Couldn't persist aggregation!", e);
            return Boolean.FALSE;
        }
    }

    @Transactional
    public void purgeEmailAggregations() {
        session.createQuery("DELETE FROM EmailAggregation").executeUpdate();
        session.flush();
    }

    @Transactional
    public void purgeAggregations() {
        session.createQuery("DELETE FROM EmailAggregation").executeUpdate();
        session.flush();
    }

    @Transactional
    public Integer purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE accountId = :accountId AND bundleName = :bundleName AND applicationName = :applicationName AND created <= :created";
        final int result = session.createQuery(query)
                .setParameter("accountId", key.getAccountId())
                .setParameter("bundleName", key.getBundle())
                .setParameter("applicationName", key.getApplication())
                .setParameter("created", lastUsedTime)
                .executeUpdate();
        session.flush();
        return result;
    }
}
