package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;

@ApplicationScoped
public class TemplateRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    public boolean isEmailSubscriptionSupported(String bundleName, String appName, EmailSubscriptionType subscriptionType) {
        if (subscriptionType == INSTANT) {
            String hql = "SELECT COUNT(*) FROM InstantEmailTemplate " +
                    "WHERE eventType.application.bundle.name = :bundleName AND eventType.application.name = :appName";
            return statelessSessionFactory.getCurrentSession().createQuery(hql, Long.class)
                    .setParameter("bundleName", bundleName)
                    .setParameter("appName", appName)
                    .getSingleResult() > 0;
        } else {
            return isEmailAggregationSupported(bundleName, appName, List.of(subscriptionType));
        }
    }

    public boolean isEmailAggregationSupported(String bundleName, String appName, List<EmailSubscriptionType> subscriptionTypes) {
        String hql = "SELECT COUNT(*) FROM AggregationEmailTemplate WHERE application.bundle.name = :bundleName " +
                "AND application.name = :appName AND id.subscriptionType IN (:subscriptionTypes)";
        return statelessSessionFactory.getCurrentSession().createQuery(hql, Long.class)
                .setParameter("bundleName", bundleName)
                .setParameter("appName", appName)
                .setParameter("subscriptionTypes", subscriptionTypes)
                .getSingleResult() > 0;
    }

    public Optional<InstantEmailTemplate> findInstantEmailTemplate(UUID eventTypeId) {
        String hql = "FROM InstantEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "WHERE t.id = :eventTypeId";
        try {
            InstantEmailTemplate emailTemplate = statelessSessionFactory.getCurrentSession().createQuery(hql, InstantEmailTemplate.class)
                    .setParameter("eventTypeId", eventTypeId)
                    .getSingleResult();
            return Optional.of(emailTemplate);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    public Optional<AggregationEmailTemplate> findAggregationEmailTemplate(String bundleName, String appName, EmailSubscriptionType subscriptionType) {
        String hql = "FROM AggregationEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "WHERE t.application.bundle.name = :bundleName AND t.application.name = :appName " +
                "AND t.id.subscriptionType = :subscriptionType";
        try {
            AggregationEmailTemplate emailTemplate = statelessSessionFactory.getCurrentSession().createQuery(hql, AggregationEmailTemplate.class)
                    .setParameter("bundleName", bundleName)
                    .setParameter("appName", appName)
                    .setParameter("subscriptionType", subscriptionType)
                    .getSingleResult();
            return Optional.of(emailTemplate);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }
}
