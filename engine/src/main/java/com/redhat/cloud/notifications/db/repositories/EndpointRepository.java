package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.StatelessSessionFactory;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EndpointType.CAMEL;
import static com.redhat.cloud.notifications.models.EndpointType.EMAIL_SUBSCRIPTION;
import static com.redhat.cloud.notifications.models.EndpointType.WEBHOOK;

@ApplicationScoped
public class EndpointRepository {

    private static final Logger LOGGER = Logger.getLogger(EndpointRepository.class);

    @Inject
    StatelessSessionFactory statelessSessionFactory;

    /**
     * The purpose of this method is to find or create an EMAIL_SUBSCRIPTION endpoint with empty properties. This
     * endpoint is used to aggregate and store in the DB the email actions outcome, which will be used later by the
     * event log. The recipients of the current email action have already been resolved before this step, possibly from
     * multiple endpoints and recipients settings. The properties created below have no impact on the resolution of the
     * action recipients.
     */
    public Endpoint getOrCreateDefaultEmailSubscription(String accountId) {
        String query = "FROM Endpoint WHERE accountId = :accountId AND compositeType.type = :endpointType";
        List<Endpoint> emailEndpoints = statelessSessionFactory.getCurrentSession().createQuery(query, Endpoint.class)
                .setParameter("accountId", accountId)
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
        endpoint.setEnabled(true);
        endpoint.setDescription("System email endpoint");
        endpoint.setName("Email endpoint");
        endpoint.setType(EMAIL_SUBSCRIPTION);
        endpoint.prePersist();
        properties.setEndpoint(endpoint);

        statelessSessionFactory.getCurrentSession().insert(endpoint);
        statelessSessionFactory.getCurrentSession().insert(endpoint.getProperties());
        return endpoint;
    }

    public List<Endpoint> getTargetEndpoints(String tenant, EventType eventType) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled IS TRUE AND b.eventType = :eventType AND (bga.behaviorGroup.accountId = :accountId OR bga.behaviorGroup.accountId IS NULL)";

        List<Endpoint> endpoints = statelessSessionFactory.getCurrentSession().createQuery(query, Endpoint.class)
                .setParameter("eventType", eventType)
                .setParameter("accountId", tenant)
                .getResultList();
        loadProperties(endpoints);
        for (Endpoint endpoint : endpoints) {
            if (endpoint.getAccountId() == null) {
                if (endpoint.getType() == EMAIL_SUBSCRIPTION) {
                    endpoint.setAccountId(tenant);
                } else {
                    LOGGER.warnf("Invalid endpoint configured in default behavior group: %s", endpoint.getId());
                }
            }
        }
        return endpoints;
    }

    public List<Endpoint> getTargetEmailSubscriptionEndpoints(String tenant, String bundleName, String applicationName, String eventTypeName) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled IS TRUE AND b.eventType.name = :eventTypeName AND (bga.behaviorGroup.accountId = :accountId OR bga.behaviorGroup.accountId IS NULL) " +
                "AND b.eventType.application.name = :applicationName AND b.eventType.application.bundle.name = :bundleName " +
                "AND e.compositeType.type = :endpointType";

        List<Endpoint> endpoints = statelessSessionFactory.getCurrentSession().createQuery(query, Endpoint.class)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .setParameter("accountId", tenant)
                .setParameter("bundleName", bundleName)
                .setParameter("endpointType", EMAIL_SUBSCRIPTION)
                .getResultList();
        loadProperties(endpoints);
        return endpoints;
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
                    // Todo: NOTIF-429 backward compatibility change - Remove soon.
                    if (typedEndpointClass.equals(CamelProperties.class)) {
                        endpoint.getProperties(CamelProperties.class).setSubType(endpoint.getSubType());
                    }
                }
            }
        }
    }
}
