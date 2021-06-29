package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@ApplicationScoped
public class EndpointResources {

    private static final Logger LOGGER = Logger.getLogger(EndpointResources.class.getName());

    @Inject
    Mutiny.Session session;

    @Inject
    Mutiny.StatelessSession statelessSession;

    public Uni<Endpoint> createEndpoint(Endpoint endpoint) {
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
                                return session.persist(endpoint.getProperties())
                                        .onItem().call(session::flush);
                            case EMAIL_SUBSCRIPTION:
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
    }

    public Uni<List<Endpoint>> getEndpointsPerType(String tenant, EndpointType type, Boolean activeOnly, Query limiter) {
        // TODO Modify the parameter to take a vararg of Functions that modify the query
        // TODO Modify to take account selective joins (JOIN (..) UNION (..)) based on the type, same for getEndpoints
        String query = "SELECT e FROM Endpoint e WHERE e.accountId = :accountId AND e.type = :endpointType";
        if (activeOnly != null) {
            query += " AND enabled = :enabled";
        }

        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<Endpoint> mutinyQuery = session.createQuery(query, Endpoint.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointType", type);

        if (activeOnly != null) {
            mutinyQuery = mutinyQuery.setParameter("enabled", activeOnly);
        }

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        return mutinyQuery.getResultList()
                .onItem().call(this::loadProperties);
    }

    public Uni<Long> getEndpointsCountPerType(String tenant, EndpointType type, Boolean activeOnly) {
        String query = "SELECT COUNT(*) FROM Endpoint WHERE accountId = :accountId AND type = :endpointType";
        if (activeOnly != null) {
            query += " AND enabled = :enabled";
        }

        Mutiny.Query<Long> mutinyQuery = session.createQuery(query, Long.class)
                .setParameter("accountId", tenant)
                .setParameter("endpointType", type);

        if (activeOnly != null) {
            mutinyQuery = mutinyQuery.setParameter("enabled", activeOnly);
        }

        return mutinyQuery.getSingleResult();
    }

    public Uni<List<Endpoint>> getTargetEndpoints(String tenant, String bundleName, String applicationName, String eventTypeName) {
        String query = "SELECT DISTINCT e FROM Endpoint e JOIN e.behaviorGroupActions bga JOIN bga.behaviorGroup.behaviors b " +
                "WHERE e.enabled = TRUE AND b.eventType.name = :eventTypeName AND bga.behaviorGroup.accountId = :accountId " +
                "AND b.eventType.application.name = :applicationName AND b.eventType.application.bundle.name = :bundleName";

        return statelessSession.createQuery(query, Endpoint.class)
                .setParameter("applicationName", applicationName)
                .setParameter("eventTypeName", eventTypeName)
                .setParameter("accountId", tenant)
                .setParameter("bundleName", bundleName)
                .getResultList()
                .onItem().call(endpoints -> loadProperties(endpoints, true));
    }

    public Uni<List<Endpoint>> getEndpoints(String tenant, Query limiter) {
        // TODO Add the ability to modify the getEndpoints to return also with JOIN to application_eventtypes_endpoints link table
        //      or should I just create a new method for it?
        String query = "SELECT e FROM Endpoint e WHERE e.accountId = :accountId";

        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<Endpoint> mutinyQuery = session.createQuery(query, Endpoint.class)
                .setParameter("accountId", tenant);

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        return mutinyQuery.getResultList()
                .onItem().call(this::loadProperties);
    }

    public Uni<Long> getEndpointsCount(String tenant) {
        String query = "SELECT COUNT(*) FROM Endpoint WHERE accountId = :accountId";
        return session.createQuery(query, Long.class)
                .setParameter("accountId", tenant)
                .getSingleResult();
    }

    public Uni<Endpoint> getEndpoint(String tenant, UUID id) {
        String query = "SELECT e FROM Endpoint e WHERE e.accountId = :accountId AND e.id = :id";
        return session.createQuery(query, Endpoint.class)
                .setParameter("id", id)
                .setParameter("accountId", tenant)
                .getSingleResultOrNull()
                .onItem().ifNotNull().transformToUni(endpoint -> loadProperties(endpoint));
    }

    public Uni<Boolean> deleteEndpoint(String tenant, UUID id) {
        String query = "DELETE FROM Endpoint WHERE accountId = :accountId AND id = :id";
        return session.createQuery(query)
                .setParameter("id", id)
                .setParameter("accountId", tenant)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
        // Actually, the endpoint targeting this should be repeatable
    }

    public Uni<Boolean> disableEndpoint(String tenant, UUID id) {
        return modifyEndpointStatus(tenant, id, false);
    }

    public Uni<Boolean> enableEndpoint(String tenant, UUID id) {
        return modifyEndpointStatus(tenant, id, true);
    }

    private Uni<Boolean> modifyEndpointStatus(String tenant, UUID id, boolean enabled) {
        String query = "UPDATE Endpoint SET enabled = :enabled WHERE accountId = :accountId AND id = :id";

        return session.createQuery(query)
                .setParameter("id", id)
                .setParameter("accountId", tenant)
                .setParameter("enabled", enabled)
                .executeUpdate()
                .call(session::flush)
                .onItem().transform(rowCount -> rowCount > 0);
    }

    public Uni<Boolean> updateEndpoint(Endpoint endpoint) {
        // TODO Update could fail because the item did not exist, throw 404 in that case?
        // TODO Fix transaction so that we don't end up with half the updates applied
        String endpointQuery = "UPDATE Endpoint SET name = :name, description = :description, enabled = :enabled " +
                "WHERE accountId = :accountId AND id = :id";
        String webhookQuery = "UPDATE WebhookProperties SET url = :url, method = :method, " +
                "disableSslVerification = :disableSslVerification, secretToken = :secretToken WHERE endpoint.id = :endpointId";
        String camelQuery = "UPDATE CamelProperties SET url = :url, subType = :subType, " +
                "disableSslVerification = :disableSslVerification, secretToken = :secretToken WHERE endpoint.id = :endpointId";

        return session.createQuery(endpointQuery)
                .setParameter("name", endpoint.getName())
                .setParameter("description", endpoint.getDescription())
                .setParameter("enabled", endpoint.isEnabled())
                .setParameter("accountId", endpoint.getAccountId())
                .setParameter("id", endpoint.getId())
                .executeUpdate()
                .call(session::flush)
                .onItem().transformToUni(endpointRowCount -> {
                    if (endpointRowCount == 0) {
                        return Uni.createFrom().item(Boolean.FALSE);
                    } else if (endpoint.getProperties() == null) {
                        return Uni.createFrom().item(Boolean.TRUE);
                    } else {
                        switch (endpoint.getType()) {
                            case WEBHOOK:
                                WebhookProperties properties = endpoint.getProperties(WebhookProperties.class);
                                return session.createQuery(webhookQuery)
                                        .setParameter("url", properties.getUrl())
                                        .setParameter("method", properties.getMethod())
                                        .setParameter("disableSslVerification", properties.getDisableSslVerification())
                                        .setParameter("secretToken", properties.getSecretToken())
                                        .setParameter("endpointId", endpoint.getId())
                                        .executeUpdate()
                                        .call(session::flush)
                                        .onItem().transform(rowCount -> rowCount > 0);
                            case CAMEL:
                                CamelProperties cAttr = (CamelProperties) endpoint.getProperties();
                                return session.createQuery(camelQuery)
                                        .setParameter("url", cAttr.getUrl())
                                        .setParameter("disableSslVerification", cAttr.getDisableSslVerification())
                                        .setParameter("secretToken", cAttr.getSecretToken())
                                        .setParameter("endpointId", endpoint.getId())
                                        .setParameter("subType", endpoint.getType())
                                        .executeUpdate()
                                        .call(session::flush)
                                        .onItem().transform(rowCount -> rowCount > 0);
                            case EMAIL_SUBSCRIPTION:
                            default:
                                return Uni.createFrom().item(Boolean.TRUE);
                        }
                    }
                });
    }

    public Uni<Void> loadProperties(List<Endpoint> endpoints) {
        return loadProperties(endpoints, false);
    }

    public Uni<Void> loadProperties(List<Endpoint> endpoints, boolean useStatelessSession) {
        if (endpoints.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        // Group endpoints in types and load in batches for each type.
        Set<Endpoint> endpointSet = new HashSet<>(endpoints);

        return this.loadTypedProperties(WebhookProperties.class, endpointSet, EndpointType.WEBHOOK, useStatelessSession)
                .chain(() -> loadTypedProperties(CamelProperties.class, endpointSet, EndpointType.CAMEL, useStatelessSession));
        // use `.chain(() -> loadTyped...)` when adding other types
    }

    private <T extends EndpointProperties> Uni<Void> loadTypedProperties(Class<T> typedEndpointClass, Set<Endpoint> endpoints, EndpointType type, boolean useStatelessSession) {
        Map<UUID, Endpoint> endpointsMap = endpoints
                .stream()
                .filter(e -> e.getType().equals(type))
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        if (endpointsMap.size() > 0) {
            return find(typedEndpointClass, endpointsMap.keySet(), useStatelessSession)
                    .onItem().invoke(propList -> propList.forEach(props -> {
                        if (props != null) {
                            Endpoint endpoint = endpointsMap.get(props.getId());
                            endpoint.setProperties(props);
                        }
                    })).replaceWith(Uni.createFrom().voidItem());
        }

        return Uni.createFrom().voidItem();
    }

    private <T extends EndpointProperties> Uni<List<T>> find(Class<T> typedEndpointClass, Set<UUID> endpointIds, boolean useStatelessSession) {
        if (useStatelessSession) {
            String query = "FROM " + typedEndpointClass.getSimpleName() + " WHERE id IN (:endpointIds)";
            return statelessSession.createQuery(query, typedEndpointClass)
                    .setParameter("endpointIds", endpointIds)
                    .getResultList();
        } else {
            return session.find(typedEndpointClass, endpointIds.toArray());
        }
    }

    public Uni<Endpoint> loadProperties(Endpoint endpoint) {
        if (endpoint == null) {
            LOGGER.warning("Endpoint properties loading attempt with a null endpoint. It should never happen, this is a bug.");
            return Uni.createFrom().nullItem();
        }
        return this.loadProperties(Collections.singletonList(endpoint))
                .replaceWith(endpoint);
    }
}
