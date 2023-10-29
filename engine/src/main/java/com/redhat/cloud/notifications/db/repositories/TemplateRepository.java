package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.models.AggregationEmailTemplate;
import com.redhat.cloud.notifications.models.InstantEmailTemplate;
import com.redhat.cloud.notifications.models.IntegrationTemplate;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.models.Template;
import io.quarkus.cache.CacheResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@ApplicationScoped
public class TemplateRepository {

    private static final List<SubscriptionType> NON_INSTANT_SUBSCRIPTION_TYPES = Arrays.stream(SubscriptionType.values())
            .filter(subscriptionType -> subscriptionType != SubscriptionType.INSTANT)
            .collect(Collectors.toList());

    @Inject
    EntityManager entityManager;

    @Inject
    FeatureFlipper featureFlipper;

    private Optional<InstantEmailTemplate> defaultEmailTemplate = null;

    @CacheResult(cacheName = "is-email-aggregation-supported")
    public boolean isEmailAggregationSupported(UUID appId) {
        String hql = "SELECT COUNT(*) FROM AggregationEmailTemplate WHERE application.id = :appId " +
                " AND subscriptionType IN (:subscriptionTypes)";
        return entityManager.createQuery(hql, Long.class)
                .setParameter("appId", appId)
                .setParameter("subscriptionTypes", NON_INSTANT_SUBSCRIPTION_TYPES)
                .getSingleResult() > 0;
    }

    @CacheResult(cacheName = "instant-email-templates")
    public Optional<InstantEmailTemplate> findInstantEmailTemplate(UUID eventTypeId) {
        String hql = "FROM InstantEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "WHERE t.eventType.id = :eventTypeId";
        try {
            InstantEmailTemplate emailTemplate = entityManager.createQuery(hql, InstantEmailTemplate.class)
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

    public Optional<AggregationEmailTemplate> findAggregationEmailTemplate(String bundleName, String appName, SubscriptionType subscriptionType) {
        String hql = "FROM AggregationEmailTemplate t JOIN FETCH t.subjectTemplate JOIN FETCH t.bodyTemplate " +
                "WHERE t.application.bundle.name = :bundleName AND t.application.name = :appName " +
                "AND t.subscriptionType = :subscriptionType";
        try {
            AggregationEmailTemplate emailTemplate = entityManager.createQuery(hql, AggregationEmailTemplate.class)
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

    // TODO Copied from notifications-backend. Let's try to stop duplicating that code ASAP.
    /**
     * Integration templates are more generic templates directly targeted at Integrations
     * other than email, like Splunk, Slack or Teams via Camel. See {@link IntegrationTemplate}
     * for more details.
     * @param appId Application name for the template applies to. Can be null.
     * @param eventTypeId Event type Id for the template applies to. Can be null.
     * @param orgId The organization id for templates that are organization specific. Need to be of Kind ORG
     * @param templateKind Kind of template requested. If it does not exist, Kind DEFAULT is returned or Optional.empty()
     * @param integrationType Type of integration requested. E.g. 'slack', 'teams' or 'splunk'
     * @return IntegrationTemplate with potential fallback or Optional.empty() if there is not even a default template.
     */
    public Optional<IntegrationTemplate> findIntegrationTemplate(UUID appId,
                                                                 UUID eventTypeId,
                                                                 String orgId,
                                                                 IntegrationTemplate.TemplateKind templateKind,
                                                                 String integrationType) {
        String hql = "FROM IntegrationTemplate it JOIN FETCH it.theTemplate " +
                "WHERE it.templateKind <= :templateKind " +
                "AND it.integrationType = :iType ";

        if (orgId != null) {
            hql += "AND (it.orgId IS NULL OR it.orgId = :orgId) ";
        } else {
            hql += "AND (it.orgId IS NULL) ";
        }

        if (eventTypeId != null) {
            hql += "AND (it.eventType IS NULL OR it.eventType.id = :eventTypeId) ";
        } else {
            hql += "AND (it.eventType IS NULL) ";
        }

        if (appId != null) {
            hql += "AND (it.application IS NULL OR it.application.id = :appId) ";
        } else {
            hql += "AND (it.application IS NULL) ";
        }

        hql += "ORDER BY it.templateKind DESC, it.orgId ASC "; // nulls in orgId go last. See https://www.postgresql.org/docs/current/queries-order.html

        TypedQuery<IntegrationTemplate> query = entityManager.createQuery(hql, IntegrationTemplate.class)
                .setParameter("templateKind", templateKind)
                .setParameter("iType", integrationType)
                .setMaxResults(1);

        if (appId != null) {
            query.setParameter("appId", appId);
        }
        if (orgId != null) {
            query.setParameter("orgId", orgId);
        }
        if (eventTypeId != null) {
            query.setParameter("eventTypeId", eventTypeId);
        }

        List<IntegrationTemplate> templates = query.getResultList();

        if (templates.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(templates.get(0));
    }
}
