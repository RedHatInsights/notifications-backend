package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.EmailSubscriptionType;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.logging.Log;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.redhat.cloud.notifications.models.EmailSubscriptionType.INSTANT;

@ApplicationScoped
public class TemplateRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    @Inject
    FeatureFlipper featureFlipper;

    private Optional<InstantEmailTemplate> defaultEmailTemplate = null;

    public boolean isEmailSubscriptionSupported(String bundleName, String appName, EmailSubscriptionType subscriptionType) {
        if (subscriptionType == INSTANT) {
            if (featureFlipper.isUseDefaultTemplate()) {
                return true;
            }

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
                "AND application.name = :appName AND subscriptionType IN (:subscriptionTypes)";
        return statelessSessionFactory.getCurrentSession().createQuery(hql, Long.class)
                .setParameter("bundleName", bundleName)
                .setParameter("appName", appName)
                .setParameter("subscriptionTypes", subscriptionTypes)
                .getSingleResult() > 0;
    }

    public Optional<InstantEmailTemplate> findInstantEmailTemplate(UUID eventTypeId) {
        String hql = "FROM InstantEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "WHERE t.eventType.id = :eventTypeId";
        try {
            InstantEmailTemplate emailTemplate = statelessSessionFactory.getCurrentSession().createQuery(hql, InstantEmailTemplate.class)
                    .setParameter("eventTypeId", eventTypeId)
                    .getSingleResult();
            return Optional.of(emailTemplate);
        } catch (NoResultException e) {
            if (featureFlipper.isUseDefaultTemplate()) {
                return getDefaultEmailTemplate();
            }

            return Optional.empty();
        }
    }

    public Optional<AggregationEmailTemplate> findAggregationEmailTemplate(String bundleName, String appName, EmailSubscriptionType subscriptionType) {
        String hql = "FROM AggregationEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "WHERE t.application.bundle.name = :bundleName AND t.application.name = :appName " +
                "AND t.subscriptionType = :subscriptionType";
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

    private Optional<InstantEmailTemplate> getDefaultEmailTemplate() {
        if (defaultEmailTemplate != null) {
            return defaultEmailTemplate;
        }

        try {
            defaultEmailTemplate = Optional.of(loadDefaultEmailTemplate());
        } catch (Exception exception) {
            defaultEmailTemplate = Optional.empty();
            Log.warn("Could not load the instant default email template", exception);
        }

        return defaultEmailTemplate;
    }

    private InstantEmailTemplate loadDefaultEmailTemplate() throws Exception {
        final String instantEmailSubjectPath = "templates/Default/instantEmailTitle.txt";
        final String instantEmailBodyPath = "templates/Default/instantEmailBody.html";

        String templateSubjectData = Files.readString(
                Paths.get(this.getClass().getClassLoader().getResource(instantEmailSubjectPath).toURI()),
                StandardCharsets.UTF_8
        );

        String templateBodyData = Files.readString(
                Paths.get(this.getClass().getClassLoader().getResource(instantEmailBodyPath).toURI()),
                StandardCharsets.UTF_8
        );

        Template templateSubject = new Template();
        templateSubject.setData(templateSubjectData);
        templateSubject.setName("default-title-instant-template");

        Template templateBody = new Template();
        templateBody.setData(templateBodyData);
        templateBody.setName("default-body-instant-template");

        InstantEmailTemplate instantEmailTemplate = new InstantEmailTemplate();
        instantEmailTemplate.setSubjectTemplate(templateSubject);
        instantEmailTemplate.setBodyTemplate(templateBody);

        return instantEmailTemplate;
    }
}
