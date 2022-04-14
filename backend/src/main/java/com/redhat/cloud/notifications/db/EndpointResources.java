package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.builder.QueryBuilder;
import com.redhat.cloud.notifications.db.builder.WhereBuilder;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.EmailSubscriptionProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.WebhookProperties;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.transaction.Transactional;
import javax.ws.rs.BadRequestException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class EndpointResources {

    private static final Logger LOGGER = Logger.getLogger(EndpointResources.class);

    @Inject
    EntityManager entityManager;

    @Transactional
    public Endpoint createEndpoint(Endpoint endpoint) {
        // Todo: NOTIF-429 backward compatibility change - Remove soon.
        if (endpoint.getType() == EndpointType.CAMEL) {
            CamelProperties properties = endpoint.getProperties(CamelProperties.class);

            if (endpoint.getSubType() == null && properties.getSubType() == null) {
                throw new BadRequestException("endpoint.subtype must have a value");
            }

            if (endpoint.getSubType() != null && properties.getSubType() != null && !properties.getSubType().equals(endpoint.getSubType())) {
                throw new BadRequestException("endpoint.subtype must be equal to endpoint.properties.subtype. Consider removing endpoint.properties.subtype as it is deprecated");
            }

            if (endpoint.getSubType() == null) {
                endpoint.setSubType(properties.getSubType());
            } else if (properties.getSubType() == null) {
                properties.setSubType(endpoint.getSubType());
            }
        }
        entityManager.persist(endpoint);
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
                    entityManager.persist(endpoint.getProperties());
                default:
                    // Do nothing.
                    break;
            }
        }
        return endpoint;
    }

    public List<Endpoint> getEndpointsPerCompositeType(String accountId, Set<CompositeEndpointType> type, Boolean activeOnly, Query limiter) {
        Query.Limit limit = limiter == null ? null : limiter.getLimit();
        Query.Sort sort = limiter == null ? null : limiter.getSort();
        List<Endpoint> endpoints = EndpointResources.queryBuilderEndpointsPerType(accountId, type, activeOnly)
                .limit(limit)
                .sort(sort)
                .build(entityManager::createQuery)
                .getResultList();
        loadProperties(endpoints);
        return endpoints;
    }

    public EndpointType getEndpointTypeById(String accountId, UUID endpointId) {
        String query = "Select e.compositeType.type from Endpoint e WHERE e.accountId = :accountId AND e.id = :endpointId";
        try {
            return entityManager.createQuery(query, EndpointType.class)
                    .setParameter("accountId", accountId)
                    .setParameter("endpointId", endpointId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    @Transactional
    public Endpoint getOrCreateEmailSubscriptionEndpoint(String accountId, EmailSubscriptionProperties properties) {
        List<Endpoint> emailEndpoints = getEndpointsPerCompositeType(accountId, Set.of(new CompositeEndpointType(EndpointType.EMAIL_SUBSCRIPTION)), null, null);
        loadProperties(emailEndpoints);
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
        endpoint.setType(EndpointType.EMAIL_SUBSCRIPTION);

        return createEndpoint(endpoint);
    }

    public Long getEndpointsCountPerCompositeType(String tenant, Set<CompositeEndpointType> type, Boolean activeOnly) {
        return EndpointResources.queryBuilderEndpointsPerType(tenant, type, activeOnly)
                .buildCount(entityManager::createQuery)
                .getSingleResult();
    }

    public List<Endpoint> getEndpoints(String tenant, Query limiter) {
        Query.Limit limit = limiter == null ? null : limiter.getLimit();
        Query.Sort sort = limiter == null ? null : limiter.getSort();

        // TODO Add the ability to modify the getEndpoints to return also with JOIN to application_eventtypes_endpoints link table
        //      or should I just create a new method for it?
        List<Endpoint> endpoints = QueryBuilder.builder(Endpoint.class)
                .alias("e")
                .where(
                        WhereBuilder.builder()
                                .and("e.accountId = :accountId", "accountId", tenant)
                )
                .limit(limit)
                .sort(sort)
                .build(entityManager::createQuery)
                .getResultList();
        loadProperties(endpoints);
        return endpoints;
    }

    public Long getEndpointsCount(String tenant) {
        String query = "SELECT COUNT(*) FROM Endpoint WHERE accountId = :accountId";
        return entityManager.createQuery(query, Long.class)
                .setParameter("accountId", tenant)
                .getSingleResult();
    }

    public Endpoint getEndpoint(String tenant, UUID id) {
        String query = "SELECT e FROM Endpoint e WHERE e.accountId = :accountId AND e.id = :id";
        try {
            Endpoint endpoint = entityManager.createQuery(query, Endpoint.class)
                    .setParameter("id", id)
                    .setParameter("accountId", tenant)
                    .getSingleResult();
            loadProperties(endpoint);
            return endpoint;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Transactional
    public boolean deleteEndpoint(String tenant, UUID id) {
        String query = "DELETE FROM Endpoint WHERE accountId = :accountId AND id = :id";
        int rowCount = entityManager.createQuery(query)
                .setParameter("id", id)
                .setParameter("accountId", tenant)
                .executeUpdate();
        return rowCount > 0;
        // Actually, the endpoint targeting this should be repeatable
    }

    public boolean disableEndpoint(String tenant, UUID id) {
        return modifyEndpointStatus(tenant, id, false);
    }

    public boolean enableEndpoint(String tenant, UUID id) {
        return modifyEndpointStatus(tenant, id, true);
    }

    @Transactional
    private boolean modifyEndpointStatus(String tenant, UUID id, boolean enabled) {
        String query = "UPDATE Endpoint SET enabled = :enabled WHERE accountId = :accountId AND id = :id";
        int rowCount = entityManager.createQuery(query)
                .setParameter("id", id)
                .setParameter("accountId", tenant)
                .setParameter("enabled", enabled)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public boolean updateEndpoint(Endpoint endpoint) {
        // TODO Update could fail because the item did not exist, throw 404 in that case?
        // TODO Fix transaction so that we don't end up with half the updates applied
        String endpointQuery = "UPDATE Endpoint SET name = :name, description = :description, enabled = :enabled " +
                "WHERE accountId = :accountId AND id = :id";
        String webhookQuery = "UPDATE WebhookProperties SET url = :url, method = :method, " +
                "disableSslVerification = :disableSslVerification, secretToken = :secretToken WHERE endpoint.id = :endpointId";
        String camelQuery = "UPDATE CamelProperties SET url = :url, extras = :extras, " +
                "basicAuthentication = :basicAuthentication, " +
                "disableSslVerification = :disableSslVerification, secretToken = :secretToken WHERE endpoint.id = :endpointId";

        if (endpoint.getType() == EndpointType.EMAIL_SUBSCRIPTION) {
            throw new RuntimeException("Unable to update an endpoint of type EMAIL_SUBSCRIPTION");
        }

        int endpointRowCount = entityManager.createQuery(endpointQuery)
                .setParameter("name", endpoint.getName())
                .setParameter("description", endpoint.getDescription())
                .setParameter("enabled", endpoint.isEnabled())
                .setParameter("accountId", endpoint.getAccountId())
                .setParameter("id", endpoint.getId())
                .executeUpdate();

        if (endpointRowCount == 0) {
            return false;
        } else if (endpoint.getProperties() == null) {
            return true;
        } else {
            switch (endpoint.getType()) {
                case WEBHOOK:
                    WebhookProperties properties = endpoint.getProperties(WebhookProperties.class);
                    return entityManager.createQuery(webhookQuery)
                            .setParameter("url", properties.getUrl())
                            .setParameter("method", properties.getMethod())
                            .setParameter("disableSslVerification", properties.getDisableSslVerification())
                            .setParameter("secretToken", properties.getSecretToken())
                            .setParameter("endpointId", endpoint.getId())
                            .executeUpdate() > 0;
                case CAMEL:
                    CamelProperties cAttr = (CamelProperties) endpoint.getProperties();
                    return entityManager.createQuery(camelQuery)
                            .setParameter("url", cAttr.getUrl())
                            .setParameter("disableSslVerification", cAttr.getDisableSslVerification())
                            .setParameter("secretToken", cAttr.getSecretToken())
                            .setParameter("endpointId", endpoint.getId())
                            .setParameter("extras", cAttr.getExtras())
                            .setParameter("basicAuthentication", cAttr.getBasicAuthentication())
                            .executeUpdate() > 0;
                default:
                    return true;
            }
        }
    }

    public void loadProperties(List<Endpoint> endpoints) {
        if (endpoints.isEmpty()) {
            return;
        }

        // Group endpoints in types and load in batches for each type.
        Set<Endpoint> endpointSet = new HashSet<>(endpoints);

        loadTypedProperties(WebhookProperties.class, endpointSet, EndpointType.WEBHOOK);
        loadTypedProperties(CamelProperties.class, endpointSet, EndpointType.CAMEL);
        loadTypedProperties(EmailSubscriptionProperties.class, endpointSet, EndpointType.EMAIL_SUBSCRIPTION);
    }

    private <T extends EndpointProperties> void loadTypedProperties(Class<T> typedEndpointClass, Set<Endpoint> endpoints, EndpointType type) {
        Map<UUID, Endpoint> endpointsMap = endpoints
                .stream()
                .filter(e -> e.getType().equals(type))
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        if (endpointsMap.isEmpty()) {
            return;
        }

        String query = "FROM " + typedEndpointClass.getSimpleName() + " WHERE id IN (:ids)";
        List<T> propList = entityManager.createQuery(query, typedEndpointClass)
                .setParameter("ids", endpointsMap.keySet())
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

    static QueryBuilder<Endpoint> queryBuilderEndpointsPerType(String accountId, Set<CompositeEndpointType> type, Boolean activeOnly) {
        Set<EndpointType> basicTypes = type.stream().filter(c -> c.getSubType() == null).map(CompositeEndpointType::getType).collect(Collectors.toSet());
        Set<CompositeEndpointType> compositeTypes = type.stream().filter(c -> c.getSubType() != null).collect(Collectors.toSet());
        return QueryBuilder
                .builder(Endpoint.class)
                .alias("e")
                .where(
                        WhereBuilder.builder()
                                .ifElse(
                                        accountId == null,
                                        WhereBuilder.builder().and("e.accountId IS NULL"),
                                        WhereBuilder.builder().and("e.accountId = :accountId", "accountId", accountId)
                                )
                                .and(
                                        WhereBuilder.builder()
                                                .ifOr(basicTypes.size() > 0, "e.compositeType.type IN (:endpointType)", "endpointType", basicTypes)
                                                .ifOr(compositeTypes.size() > 0, "e.compositeType IN (:compositeTypes)", "compositeTypes", compositeTypes)
                                )
                                .ifAnd(activeOnly != null, "e.enabled = :enabled", "enabled", activeOnly)
                );
    }

    private void loadProperties(Endpoint endpoint) {
        if (endpoint == null) {
            LOGGER.warn("Endpoint properties loading attempt with a null endpoint. It should never happen, this is a bug.");
        }
        loadProperties(Collections.singletonList(endpoint));
    }
}
