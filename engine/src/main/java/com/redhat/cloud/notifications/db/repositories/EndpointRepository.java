package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.logging.Log;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EndpointStatus.READY;
import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;
import static javax.persistence.LockModeType.PESSIMISTIC_WRITE;

@ApplicationScoped
public class EndpointRepository {

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    /**
     * The purpose of this method is to find or create an EMAIL_SUBSCRIPTION endpoint with empty properties. This
     * endpoint is used to aggregate and store in the DB the email actions outcome, which will be used later by the
     * event log. The recipients of the current email action have already been resolved before this step, possibly from
     * multiple endpoints and recipients settings. The properties created below have no impact on the resolution of the
     * action recipients.
     */
    public Endpoint getOrCreateDefaultEmailSubscription(String accountId, String orgId) {
        String query = "FROM Endpoint WHERE orgId = :orgId AND compositeType.type = :endpointType";
        List<Endpoint> emailEndpoints = statelessSessionFactory.getCurrentSession().createQuery(query, Endpoint.class)
                .setParameter("orgId", orgId)
                .setParameter("endpointType", EMAIL_SUBSCRIPTION)
                .getResultList();
        loadProperties(emailEndpoints);

        EmailSubscriptionProperties properties = new EmailSubscriptionProperties();
        Optional<Endpoint> endpointOptional = emailEndpoints
                .stream()
                .filter(endpoint -> properties.hasSameProperties(endpoint.getProperties(EmailSubscriptionProperties.class)))
                .findFirst();
        if (endpointOptional.isPresent()) {
            return endpointOptional.get();
        }

        Endpoint endpoint = new Endpoint();
        endpoint.setProperties(properties);
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);
        endpoint.setEnabled(true);
        endpoint.setDescription("System email endpoint");
        endpoint.setName("Email endpoint");
        endpoint.setType(EMAIL_SUBSCRIPTION);
        endpoint.setStatus(READY);
        endpoint.prePersist();
        properties.setEndpoint(endpoint);

        statelessSessionFactory.getCurrentSession().insert(endpoint);
        statelessSessionFactory.getCurrentSession().insert(endpoint.getProperties());
        return endpoint;
    }

    public List<Endpoint> getTargetEndpoints(String orgId, EventType eventType) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled IS TRUE AND b.eventType = :eventType AND (bga.behaviorGroup.orgId = :orgId OR bga.behaviorGroup.orgId IS NULL)";

        List<Endpoint> endpoints = statelessSessionFactory.getCurrentSession().createQuery(query, Endpoint.class)
                .setParameter("eventType", eventType)
                .setParameter("orgId", orgId)
                .getResultList();
        loadProperties(endpoints);
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getOrgId() == null) {
                if (endpoint.getType() == EMAIL_SUBSCRIPTION) {
                    endpoint.setOrgId(orgId);
                } else {
                    Log.warnf("Invalid endpoint configured in default behavior group: %s", endpoint.getId());
                }
            }
        }
        return endpoints;
    }

    public List<Endpoint> getTargetEmailSubscriptionEndpoints(String orgId, String bundleName, String applicationName, String eventTypeName) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled IS TRUE AND b.eventType.name = :eventTypeName AND (bga.behaviorGroup.orgId = :orgId OR bga.behaviorGroup.orgId IS NULL) " +
                "AND b.eventType.application.name = :applicationName AND b.eventType.application.bundle.name = :bundleName " +
                "AND e.compositeType.type = :endpointType";

        List<Endpoint> endpoints = statelessSessionFactory.getCurrentSession().createQuery(query, Endpoint.class)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .setParameter("orgId", orgId)
                .setParameter("bundleName", bundleName)
                .setParameter("endpointType", EMAIL_SUBSCRIPTION)
                .getResultList();
        loadProperties(endpoints);
        return endpoints;
    }

    /**
     * Increments the server errors counter of the endpoint identified by the given ID.
     * @param endpointId the endpoint ID
     * @param maxServerErrors the maximum server errors allowed from the configuration
     * @return {@code true} if the endpoint was disabled by this method, {@code false} otherwise
     */
    @Transactional
    public boolean incrementEndpointServerErrors(UUID endpointId, int maxServerErrors) {
        /*
         * This method must be an atomic operation from a DB perspective. Otherwise, we could send multiple email
         * notifications about the same disabled endpoint in case of failures happening on concurrent threads or pods.
         */
        Optional<Endpoint> endpoint = lockEndpoint(endpointId);
        /*
         * The endpoint should always be present unless it's been deleted recently from another thread or pod.
         * It may or may not have been disabled already from the frontend or because of a 4xx error.
         */
        if (endpoint.isPresent() && endpoint.get().isEnabled()) {
            if (endpoint.get().getServerErrors() + 1 > maxServerErrors) {
                /*
                 * The endpoint exceeded the max server errors allowed from configuration.
                 * It is therefore disabled.
                 */
                String hql = "UPDATE Endpoint SET enabled = FALSE WHERE id = :id AND enabled IS TRUE";
                int updated = statelessSessionFactory.getCurrentSession().createQuery(hql)
                        .setParameter("id", endpointId)
                        .executeUpdate();
                return updated > 0;
            } else {
                /*
                 * The endpoint did NOT exceed the max server errors allowed from configuration.
                 * The errors counter is therefore incremented.
                 */
                String hql = "UPDATE Endpoint SET serverErrors = serverErrors + 1 WHERE id = :id";
                statelessSessionFactory.getCurrentSession().createQuery(hql)
                        .setParameter("id", endpointId)
                        .executeUpdate();
                return false;
            }
        } else {
            return false;
        }
    }

    private Optional<Endpoint> lockEndpoint(UUID endpointId) {
        String hql = "FROM Endpoint WHERE id = :id";
        try {
            Endpoint endpoint = statelessSessionFactory.getCurrentSession().createQuery(hql, Endpoint.class)
                    .setParameter("id", endpointId)
                    /*
                     * The endpoint will be locked by a "SELECT FOR UPDATE", preventing other threads or pods
                     * from updating it until the current transaction is complete.
                     */
                    .setLockMode(PESSIMISTIC_WRITE)
                    .getSingleResult();
            return Optional.of(endpoint);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Resets the server errors DB counter of the endpoint identified by the given ID.
     * @param endpointId the endpoint ID
     * @return {@code true} if the counter was reset by this method, {@code false} otherwise
     */
    @Transactional
    public boolean resetEndpointServerErrors(UUID endpointId) {
        String hql = "UPDATE Endpoint SET serverErrors = 0 WHERE id = :id AND serverErrors > 0";
        int updated = statelessSessionFactory.getCurrentSession().createQuery(hql)
                .setParameter("id", endpointId)
                .executeUpdate();
        return updated > 0;
    }

    /**
     * Disables the endpoint identified by the given ID.
     * @param endpointId the endpoint ID
     * @return {@code true} if the endpoint was disabled by this method, {@code false} otherwise
     */
    @Transactional
    public boolean disableEndpoint(UUID endpointId) {
        String hql = "UPDATE Endpoint SET enabled = FALSE WHERE id = :id AND enabled IS TRUE";
        int updated = statelessSessionFactory.getCurrentSession().createQuery(hql)
                .setParameter("id", endpointId)
                .executeUpdate();
        return updated > 0;
    }

    private void loadProperties(List<Endpoint> endpoints) {
        if (!endpoints.isEmpty()) {
            // Group endpoints in types and load in batches for each type.
            Set<Endpoint> endpointSet = new HashSet<>(endpoints);

            loadTypedProperties(WebhookProperties.class, endpointSet, WEBHOOK);
            loadTypedProperties(CamelProperties.class, endpointSet, CAMEL);
            loadTypedProperties(EmailSubscriptionProperties.class, endpointSet, EMAIL_SUBSCRIPTION);
        }
    }

    private <T extends EndpointProperties> void loadTypedProperties(Class<T> typedEndpointClass, Set<Endpoint> endpoints, EndpointType type) {
        Map<UUID, Endpoint> endpointsMap = endpoints
                .stream()
                .filter(e -> e.getType().equals(type))
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        if (endpointsMap.size() > 0) {
            String hql = "FROM " + typedEndpointClass.getSimpleName() + " WHERE id IN (:endpointIds)";
            List<T> propList = statelessSessionFactory.getCurrentSession().createQuery(hql, typedEndpointClass)
                    .setParameter("endpointIds", endpointsMap.keySet())
                    .getResultList();
            for (T props : propList) {
                if (props != null) {
                    Endpoint endpoint = endpointsMap.get(props.getId());
                    endpoint.setProperties(props);
                }
            }
        }
    }
}
