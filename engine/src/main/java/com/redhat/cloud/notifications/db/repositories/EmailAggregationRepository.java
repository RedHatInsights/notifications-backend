package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import io.quarkus.logging.Log;
import org.hibernate.query.Query;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class EmailAggregationRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    FeatureFlipper featureFlipper;

    public boolean addEmailAggregation(EmailAggregation aggregation) {
        aggregation.prePersist(); // This method must be called manually while using a StatelessSession.
        try {
            statelessSessionFactory.getCurrentSession().insert(aggregation);
            return true;
        } catch (Exception e) {
            Log.warn("Email aggregation persisting failed", e);
            return false;
        }
    }

    public List<EmailAggregation> getEmailAggregation(EmailAggregationKey key, LocalDateTime start, LocalDateTime end) {
        String query = "FROM EmailAggregation WHERE orgId = :orgId AND bundleName = :bundleName AND applicationName = :applicationName " +
            (featureFlipper.isUseEventTypeForAggregationEnabled() ? "AND ((:eventType is null and eventType is null) or eventType = :eventType) " : "") +
            "AND created > :start AND created <= :end ORDER BY created";
        Query queryProducer = statelessSessionFactory.getCurrentSession().createQuery(query, EmailAggregation.class)
            .setParameter("orgId", key.getOrgId())
            .setParameter("bundleName", key.getBundle())
            .setParameter("applicationName", key.getApplication())
            .setParameter("start", start)
            .setParameter("end", end);
        if (featureFlipper.isUseEventTypeForAggregationEnabled()) {
            queryProducer.setParameter("eventType", key.getEventType());
        }

        return queryProducer.getResultList();
    }

    @Transactional
    public int purgeOldAggregation(EmailAggregationKey key, LocalDateTime lastUsedTime) {
        String query = "DELETE FROM EmailAggregation WHERE orgId = :orgId AND bundleName = :bundleName AND applicationName = :applicationName " +
            (featureFlipper.isUseEventTypeForAggregationEnabled() ? "AND ((:eventType is null and eventType is null) or eventType = :eventType) " : "") +
            "AND created <= :created";

        Query queryProducer = statelessSessionFactory.getCurrentSession().createQuery(query)
            .setParameter("orgId", key.getOrgId())
            .setParameter("bundleName", key.getBundle())
            .setParameter("applicationName", key.getApplication())
            .setParameter("created", lastUsedTime);

        if (featureFlipper.isUseEventTypeForAggregationEnabled()) {
            queryProducer.setParameter("eventType", key.getEventType());
        }

        return queryProducer.executeUpdate();
    }
}
