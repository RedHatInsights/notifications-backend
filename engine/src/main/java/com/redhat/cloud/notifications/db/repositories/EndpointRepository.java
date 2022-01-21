package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.session.CommonStateSessionFactory;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;
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

@ApplicationScoped
public class EndpointRepository {

    private static final Logger LOGGER = Logger.getLogger(EndpointRepository.class);

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    CommonStateSessionFactory commonStateSessionFactory;

    public Uni<Endpoint> createEndpoint(Endpoint endpoint, boolean useStatelessSession) {
        return commonStateSessionFactory.withSession(useStatelessSession, session -> {
            return session.persist(endpoint)
                    .onItem().call(session::flush)
                    .onItem().call(() -> {
                        // If the endpoint properties are null, they won't be persisted.
                        if (endpoint.getProperties() != null) {
                            /*
                             * As weird as it seems, we need the following line because the Endpoint instance was
                             * deserialized from JSON and that JSON did not contain any information about the
                             * @OneToOne relation from EndpointProperties to Endpoint.
                             */
                            endpoint.getProperties().setEndpoint(endpoint);
                            switch (endpoint.getType()) {
                                case CAMEL:
                                case WEBHOOK:
                                case EMAIL_SUBSCRIPTION:
                                    return session.persist(endpoint.getProperties())
                                            .onItem().call(session::flush);
                                default:
                                    // Do nothing.
                                    break;
                            }
                        }
                        /*
                         * If this line is reached, it means the endpoint properties are null or we don't support
                         * persisting properties for the endpoint type. We still have to return something.
                         */
                        return Uni.createFrom().voidItem();
                    })
                    .replaceWith(endpoint);
        });
    }

    public Uni<List<Endpoint>> getEndpointsPerType(String accountId, Set<EndpointType> type, Boolean activeOnly, Query limiter, boolean useStatelessSession) {
        return commonStateSessionFactory.withSession(useStatelessSession, session -> {
            // TODO Modify the parameter to take a vararg of Functions that modify the query
            // TODO Modify to take account selective joins (JOIN (..) UNION (..)) based on the type, same for getEndpoints
            String query = "SELECT e FROM Endpoint e WHERE e.type IN (:endpointType)";
            if (accountId == null) {
                query += " AND e.accountId IS NULL";
            } else {
                query += " AND e.accountId = :accountId";
            }
            if (activeOnly != null) {
                query += " AND enabled = :enabled";
            }

            if (limiter != null) {
                query = limiter.getModifiedQuery(query);
            }

            Mutiny.Query<Endpoint> mutinyQuery = session.createQuery(query, Endpoint.class)
                    .setParameter("endpointType", type);

            if (accountId != null) {
                mutinyQuery = mutinyQuery.setParameter("accountId", accountId);
            }
            if (activeOnly != null) {
                mutinyQuery = mutinyQuery.setParameter("enabled", activeOnly);
            }

            if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
                mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                        .setFirstResult(limiter.getLimit().getOffset());
            }

            return mutinyQuery.getResultList()
                    .onItem().call(endpoints -> loadProperties(endpoints, useStatelessSession));
        });
    }

    public Uni<Endpoint> getOrCreateEmailSubscriptionEndpoint(String accountId, EmailSubscriptionProperties properties, boolean useStatelessSession) {
        return commonStateSessionFactory.withSession(useStatelessSession, session -> {
            return getEndpointsPerType(accountId, Set.of(EndpointType.EMAIL_SUBSCRIPTION), null, null, useStatelessSession)
                    .onItem().call(endpoints -> loadProperties(endpoints, useStatelessSession))
                    .onItem().transformToUni(emailEndpoints -> {
                        Optional<Endpoint> endpointOptional = emailEndpoints
                                .stream()
                                .filter(endpoint -> properties.hasSameProperties(endpoint.getProperties(EmailSubscriptionProperties.class)))
                                .findFirst();
                        if (endpointOptional.isPresent()) {
                            return Uni.createFrom().item(endpointOptional.get());
                        }

                        Endpoint endpoint = new Endpoint();
                        endpoint.setProperties(properties);
                        endpoint.setAccountId(accountId);
                        endpoint.setEnabled(true);
                        endpoint.setDescription("System email endpoint");
                        endpoint.setName("Email endpoint");
                        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

                        return createEndpoint(endpoint, useStatelessSession);
                    });
        });
    }

    // Note: This method uses a stateless session
    public Uni<List<Endpoint>> getTargetEndpoints(String tenant, EventType eventType) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled = TRUE AND b.eventType = :eventType AND (bga.behaviorGroup.accountId = :accountId OR bga.behaviorGroup.accountId IS NULL)";

        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery(query, Endpoint.class)
                    .setParameter("eventType", eventType)
                    .setParameter("accountId", tenant)
                    .getResultList()
                    .onItem().call(endpoints -> loadProperties(endpoints, true))
                    .invoke(endpoints -> {
                        for (Endpoint endpoint : endpoints) {
                            if (endpoint.getAccountId() == null) {
                                if (endpoint.getType() == EndpointType.EMAIL_SUBSCRIPTION) {
                                    endpoint.setAccountId(tenant);
                                } else {
                                    LOGGER.warnf("Invalid endpoint configured in default behavior group: %s", endpoint.getId());
                                }
                            }
                        }
                    });
        });
    }

    // Note: This method uses a stateless session
    public Uni<List<Endpoint>> getTargetEndpointsFromType(String tenant, String bundleName, String applicationName, String eventTypeName, EndpointType endpointType) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled = TRUE AND b.eventType.name = :eventTypeName AND (bga.behaviorGroup.accountId = :accountId OR bga.behaviorGroup.accountId IS NULL) " +
                "AND b.eventType.application.name = :applicationName AND b.eventType.application.bundle.name = :bundleName " +
                "AND e.type = :endpointType";

        return sessionFactory.withStatelessSession(statelessSession -> {
            return statelessSession.createQuery(query, Endpoint.class)
                    .setParameter("applicationName", applicationName)
                    .setParameter("eventTypeName", eventTypeName)
                    .setParameter("accountId", tenant)
                    .setParameter("bundleName", bundleName)
                    .setParameter("endpointType", endpointType)
                    .getResultList()
                    .onItem().call(endpoints -> loadProperties(endpoints, true));
        });
    }

    private Uni<Void> loadProperties(List<Endpoint> endpoints, boolean useStatelessSession) {
        if (endpoints.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        // Group endpoints in types and load in batches for each type.
        Set<Endpoint> endpointSet = new HashSet<>(endpoints);

        return this.loadTypedProperties(WebhookProperties.class, endpointSet, EndpointType.WEBHOOK, useStatelessSession)
                .chain(() -> loadTypedProperties(CamelProperties.class, endpointSet, EndpointType.CAMEL, useStatelessSession))
                .chain(() -> loadTypedProperties(EmailSubscriptionProperties.class, endpointSet, EndpointType.EMAIL_SUBSCRIPTION, useStatelessSession));
    }

    private <T extends EndpointProperties> Uni<Void> loadTypedProperties(Class<T> typedEndpointClass, Set<Endpoint> endpoints, EndpointType type, boolean useStatelessSession) {
        Map<UUID, Endpoint> endpointsMap = endpoints
                .stream()
                .filter(e -> e.getType().equals(type))
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        if (endpointsMap.size() > 0) {
            return commonStateSessionFactory.withSession(
                useStatelessSession,
                commonStateSession -> commonStateSession.find(typedEndpointClass, endpointsMap.keySet().toArray())
            ).onItem().invoke(propList -> propList.forEach(props -> {
                if (props != null) {
                    Endpoint endpoint = endpointsMap.get(props.getId());
                    endpoint.setProperties(props);
                }
            })).replaceWith(Uni.createFrom().voidItem());
        }

        return Uni.createFrom().voidItem();
    }
}
