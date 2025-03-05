package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.config.BackendConfig;
import com.redhat.cloud.notifications.db.Query;
import com.redhat.cloud.notifications.db.builder.QueryBuilder;
import com.redhat.cloud.notifications.db.builder.WhereBuilder;
import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.CompositeEndpointType;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.EndpointType;
import com.redhat.cloud.notifications.models.PagerDutyProperties;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.TypedQuery;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.redhat.cloud.notifications.models.EndpointStatus.READY;

@ApplicationScoped
public class EndpointRepository {
    @Inject
    EntityManager entityManager;

    @Inject
    BackendConfig backendConfig;

    public void checkEndpointNameDuplicate(Endpoint endpoint) {
        if (endpoint.getType() != null && endpoint.getType().isSystemEndpointType) {
            // This check does not apply for email subscriptions - as these are managed by us.
            return;
        }

        String hql = "SELECT COUNT(*) FROM Endpoint WHERE name = :name AND orgId = :orgId";
        if (endpoint.getId() != null) {
            hql += " AND id != :endpointId";
        }

        TypedQuery<Long> query = entityManager.createQuery(hql, Long.class)
                .setParameter("name", endpoint.getName())
                .setParameter("orgId", endpoint.getOrgId());

        if (endpoint.getId() != null) {
            query.setParameter("endpointId", endpoint.getId());
        }

        if (query.getSingleResult() > 0) {
            throw new BadRequestException("An endpoint with name [" + endpoint.getName() + "] already exists");
        }
    }

    @Transactional
    public Endpoint createEndpoint(Endpoint endpoint) {
        checkEndpointNameDuplicate(endpoint);
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
                case ANSIBLE:
                case CAMEL:
                case WEBHOOK:
                case EMAIL_SUBSCRIPTION:
                case DRAWER:
                case PAGERDUTY:
                    entityManager.persist(endpoint.getProperties());
                default:
                    // Do nothing.
                    break;
            }
        }
        return endpoint;
    }

    public List<Endpoint> getEndpointsPerCompositeType(String orgId, @Nullable String name, Set<CompositeEndpointType> type, Boolean activeOnly, Query limiter, final Set<UUID> authorizedIds) {
        if (limiter != null) {
            limiter.setSortFields(Endpoint.SORT_FIELDS);
        }

        Query.Limit limit = limiter == null ? null : limiter.getLimit();
        Optional<Query.Sort> sort = limiter == null ? Optional.empty() : limiter.getSort();

        Log.debugf("[org_id: %s][name: %s][composite_type: %s][active_only: %s][query: %s][authorized_ids: %s] Looking up endpoints in our database", orgId, name, type, activeOnly, limiter, authorizedIds);
        List<Endpoint> endpoints = EndpointRepository.queryBuilderEndpointsPerType(orgId, name, type, activeOnly, authorizedIds)
                .limit(limit)
                .sort(sort)
                .build(entityManager::createQuery)
                .getResultList();
        loadProperties(endpoints);

        Log.debugf("[org_id: %s] Returning list of endpoints: %s", orgId, endpoints);

        return endpoints;
    }

    public EndpointType getEndpointTypeById(String orgId, UUID endpointId) {
        String query = "Select e.compositeType.type from Endpoint e WHERE e.orgId = :orgId AND e.id = :endpointId";
        try {
            return entityManager.createQuery(query, EndpointType.class)
                    .setParameter("orgId", orgId)
                    .setParameter("endpointId", endpointId)
                    .getSingleResult();
        } catch (NoResultException e) {
            throw new NotFoundException("Endpoint not found");
        }
    }

    @Transactional
    public Optional<Endpoint> getSystemSubscriptionEndpoint(String orgId, SystemSubscriptionProperties properties, EndpointType endpointType) {
        List<Endpoint> endpoints = getEndpointsPerCompositeType(orgId, null, Set.of(new CompositeEndpointType(endpointType)), null, null, null);
        loadProperties(endpoints);
        return endpoints
            .stream()
            .filter(endpoint -> properties.hasSameProperties(endpoint.getProperties(SystemSubscriptionProperties.class)))
            .findFirst();
    }

    @Transactional
    public Endpoint createSystemSubscriptionEndpoint(String accountId, String orgId, SystemSubscriptionProperties properties, EndpointType endpointType) {
        String label = "Email";
        if (EndpointType.DRAWER == endpointType) {
            label = "Drawer";
        }
        List<Endpoint> endpoints = getEndpointsPerCompositeType(orgId, null, Set.of(new CompositeEndpointType(endpointType)), null, null, null);
        loadProperties(endpoints);
        Optional<Endpoint> endpointOptional = endpoints
            .stream()
            .filter(endpoint -> properties.hasSameProperties(endpoint.getProperties(SystemSubscriptionProperties.class)))
            .findFirst();
        if (endpointOptional.isPresent()) {
            return endpointOptional.get();
        }

        // In order to avoid having duplicated names which could end up in the
        // "unique endpoint name" constraint being triggered, we generate and
        // assign the endpoint's UUID ourselves.
        final UUID endpointId = UUID.randomUUID();

        final Endpoint endpoint = new Endpoint();
        endpoint.setId(endpointId);
        endpoint.setProperties(properties);
        endpoint.setAccountId(accountId);
        endpoint.setOrgId(orgId);
        endpoint.setEnabled(true);
        endpoint.setDescription(String.format("System %s endpoint", label.toLowerCase()));
        endpoint.setName(String.format("%s endpoint %s", label, endpointId));
        endpoint.setType(endpointType);
        endpoint.setStatus(READY);

        return createEndpoint(endpoint);
    }

    public Long getEndpointsCountPerCompositeType(String orgId, @Nullable String name, Set<CompositeEndpointType> type, Boolean activeOnly, final Set<UUID> authorizedIds) {
        return EndpointRepository.queryBuilderEndpointsPerType(orgId, name, type, activeOnly, authorizedIds)
                .buildCount(entityManager::createQuery)
                .getSingleResult();
    }

    public Endpoint getEndpoint(String orgId, UUID id) {
        String query = "SELECT e FROM Endpoint e WHERE e.orgId = :orgId AND e.id = :id";
        try {
            Endpoint endpoint = entityManager.createQuery(query, Endpoint.class)
                    .setParameter("id", id)
                    .setParameter("orgId", orgId)
                    .getSingleResult();
            loadProperties(endpoint);
            return endpoint;
        } catch (NoResultException e) {
            return null;
        }
    }

    @Transactional
    public boolean deleteEndpoint(String orgId, UUID id) {
        String query = "DELETE FROM Endpoint WHERE orgId = :orgId AND id = :id";
        int rowCount = entityManager.createQuery(query)
                .setParameter("id", id)
                .setParameter("orgId", orgId)
                .executeUpdate();
        return rowCount > 0;
        // Actually, the endpoint targeting this should be repeatable
    }

    public boolean disableEndpoint(String orgId, UUID id) {
        return modifyEndpointStatus(orgId, id, false);
    }

    public boolean enableEndpoint(String orgId, UUID id) {
        return modifyEndpointStatus(orgId, id, true);
    }

    @Transactional
    boolean modifyEndpointStatus(String orgId, UUID id, boolean enabled) {
        String query = "UPDATE Endpoint SET enabled = :enabled, serverErrors = 0 WHERE orgId = :orgId AND id = :id";
        int rowCount = entityManager.createQuery(query)
                .setParameter("id", id)
                .setParameter("orgId", orgId)
                .setParameter("enabled", enabled)
                .executeUpdate();
        return rowCount > 0;
    }

    @Transactional
    public boolean updateEndpoint(Endpoint endpoint) {
        checkEndpointNameDuplicate(endpoint);
        // TODO Update could fail because the item did not exist, throw 404 in that case?
        // TODO Fix transaction so that we don't end up with half the updates applied
        String endpointQuery = "UPDATE Endpoint SET name = :name, description = :description, enabled = :enabled, serverErrors = 0 " +
                "WHERE orgId = :orgId AND id = :id";
        String webhookQuery = "UPDATE WebhookProperties SET url = :url, method = :method, " +
                "disableSslVerification = :disableSslVerification WHERE endpoint.id = :endpointId";
        String camelQuery = "UPDATE CamelProperties SET url = :url, extras = :extras, " +
                "disableSslVerification = :disableSslVerification WHERE endpoint.id = :endpointId";
        String pagerDutyQuery = "UPDATE PagerDutyProperties SET severity = :severity " +
                "WHERE endpoint.id = :endpointId";

        if (endpoint.getType() != null && endpoint.getType().isSystemEndpointType) {
            throw new RuntimeException("Unable to update a system endpoint of type " + endpoint.getType());
        }

        int endpointRowCount = entityManager.createQuery(endpointQuery)
                .setParameter("name", endpoint.getName())
                .setParameter("description", endpoint.getDescription())
                .setParameter("enabled", endpoint.isEnabled())
                .setParameter("orgId", endpoint.getOrgId())
                .setParameter("id", endpoint.getId())
                .executeUpdate();

        if (endpointRowCount == 0) {
            return false;
        } else if (endpoint.getProperties() == null) {
            return true;
        } else {
            switch (endpoint.getType()) {
                case ANSIBLE:
                case WEBHOOK:
                    WebhookProperties properties = endpoint.getProperties(WebhookProperties.class);
                    return entityManager.createQuery(webhookQuery)
                            .setParameter("url", properties.getUrl())
                            .setParameter("method", properties.getMethod())
                            .setParameter("disableSslVerification", properties.getDisableSslVerification())
                            .setParameter("endpointId", endpoint.getId())
                            .executeUpdate() > 0;
                case CAMEL:
                    CamelProperties cAttr = (CamelProperties) endpoint.getProperties();
                    return entityManager.createQuery(camelQuery)
                            .setParameter("url", cAttr.getUrl())
                            .setParameter("disableSslVerification", cAttr.getDisableSslVerification())
                            .setParameter("endpointId", endpoint.getId())
                            .setParameter("extras", cAttr.getExtras())
                            .executeUpdate() > 0;
                case PAGERDUTY:
                    PagerDutyProperties pdAttr = (PagerDutyProperties) endpoint.getProperties();
                    return entityManager.createQuery(pagerDutyQuery)
                            .setParameter("severity", pdAttr.getSeverity())
                            .setParameter("endpointId", endpoint.getId())
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

        loadTypedProperties(WebhookProperties.class, endpointSet, EndpointType.ANSIBLE);
        loadTypedProperties(WebhookProperties.class, endpointSet, EndpointType.WEBHOOK);
        loadTypedProperties(CamelProperties.class, endpointSet, EndpointType.CAMEL);
        loadTypedProperties(SystemSubscriptionProperties.class, endpointSet, EndpointType.EMAIL_SUBSCRIPTION);
        loadTypedProperties(SystemSubscriptionProperties.class, endpointSet, EndpointType.DRAWER);
        loadTypedProperties(PagerDutyProperties.class, endpointSet, EndpointType.PAGERDUTY);
    }

    private <T extends EndpointProperties> void loadTypedProperties(Class<T> typedEndpointClass, Set<Endpoint> endpoints, EndpointType type) {
        Map<UUID, Endpoint> endpointsMap = endpoints
                .stream()
                .filter(e -> e.getType().equals(type))
                .collect(Collectors.toMap(Endpoint::getId, Function.identity()));

        if (endpointsMap.size() > 0) {
            String query = "FROM " + typedEndpointClass.getSimpleName() + " WHERE id IN (:ids)";
            List<T> propList = entityManager.createQuery(query, typedEndpointClass)
                    .setParameter("ids", endpointsMap.keySet())
                    .getResultList();
            for (T props : propList) {
                if (props != null) {
                    Endpoint endpoint = endpointsMap.get(props.getId());
                    endpoint.setProperties(props);
                }
            }
        }
    }

    static QueryBuilder<Endpoint> queryBuilderEndpointsPerType(String orgId, @Nullable String name, Set<CompositeEndpointType> type, Boolean activeOnly, final Set<UUID> authorizedIds) {
        Set<EndpointType> basicTypes = type.stream().filter(c -> c.getSubType() == null).map(CompositeEndpointType::getType).collect(Collectors.toSet());
        Set<CompositeEndpointType> compositeTypes = type.stream().filter(c -> c.getSubType() != null).collect(Collectors.toSet());
        return QueryBuilder
                .builder(Endpoint.class)
                .alias("e")
                .where(
                        WhereBuilder.builder()
                                .ifElse(
                                        orgId == null,
                                        WhereBuilder.builder().and("e.orgId IS NULL"),
                                        WhereBuilder.builder().and("e.orgId = :orgId", "orgId", orgId)
                                )
                                .and(
                                        WhereBuilder.builder()
                                                .ifOr(basicTypes.size() > 0, "e.compositeType.type IN (:endpointType)", "endpointType", basicTypes)
                                                .ifOr(compositeTypes.size() > 0, "e.compositeType IN (:compositeTypes)", "compositeTypes", compositeTypes)
                                )
                                .ifAnd(authorizedIds != null, "e.id IN (:authorizedIds)", "authorizedIds", authorizedIds)
                                .ifAnd(activeOnly != null, "e.enabled = :enabled", "enabled", activeOnly)
                                .ifAnd(
                                        name != null && !name.isEmpty(),
                                        "LOWER(e.name) LIKE :name",
                                        "name", (Supplier<String>) () -> "%" + name.toLowerCase() + "%"
                                )
                );
    }

    public Endpoint loadProperties(Endpoint endpoint) {
        if (endpoint == null) {
            Log.warn("Endpoint properties loading attempt with a null endpoint. It should never happen, this is a bug.");
            return null;
        }
        loadProperties(Collections.singletonList(endpoint));
        return endpoint;
    }

    /**
     * Checks if an endpoint exists in the database.
     * @param endpointUuid the UUID to look by.
     * @param orgId the OrgID to filter the endpoints with.
     * @return true if it exists, false otherwise.
     */
    public boolean existsByUuidAndOrgId(final UUID endpointUuid, final String orgId) {
        final String existsEndpointByUuid =
            "SELECT " +
                "1 " +
            "FROM " +
                "Endpoint AS e " +
            "WHERE " +
                "e.id = :endpointUuid " +
            "AND " +
                "e.orgId = :orgId";

        try {
            this.entityManager.createQuery(existsEndpointByUuid)
                .setParameter("endpointUuid", endpointUuid)
                .setParameter("orgId", orgId)
                .getSingleResult();

            return true;
        } catch (final NoResultException e) {
            return false;
        }
    }

    public Optional<Endpoint> getEndpointWithLinkedEventTypes(String orgId, UUID id) {
        String query = "SELECT e FROM Endpoint e " +
            "LEFT JOIN FETCH e.eventTypes ep " +
            "LEFT JOIN FETCH ep.application ap " +
            "LEFT JOIN FETCH ap.bundle " +
            "WHERE e.orgId = :orgId AND e.id = :id";
        try {
            Endpoint endpoint = entityManager.createQuery(query, Endpoint.class)
                .setParameter("id", id)
                .setParameter("orgId", orgId)
                .getSingleResult();
            loadProperties(endpoint);
            return Optional.of(endpoint);
        } catch (NoResultException e) {
            return Optional.empty();
        }
    }

    /**
     * Retrieves a list of endpoints ordered by their creadtion date in
     * ascending order —oldest first—.
     * @param orgId the organization to filter the integrations by.
     * @param limit the size of the list to retrieve.
     * @param offset the offset we should apply to skip the already read
     *               endpoints.
     * @return a list of endpoints.
     */
    public List<Endpoint> getNonSystemEndpointsByOrgIdWithLimitAndOffset(final Optional<String> orgId, final int limit, final int offset) {
        final StringBuilder queryDefinition = new StringBuilder();
        queryDefinition.append("FROM Endpoint ");

        orgId.ifPresentOrElse(
            s -> queryDefinition.append("WHERE orgId = :orgId "),
            () -> queryDefinition.append("WHERE orgId IS NOT NULL ")
        );

        queryDefinition.append("ORDER BY created ASC");

        final TypedQuery<Endpoint> query = this.entityManager.createQuery(queryDefinition.toString(), Endpoint.class);
        orgId.ifPresent(s -> query.setParameter("orgId", s));

        return query
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    }

    public List<String> getOrgIdWithEndpoints() {
        String query = "SELECT distinct(orgId) FROM Endpoint where orgId IS NOT null";
        return entityManager.createQuery(query, String.class)
                .getResultList();
    }

    public List<UUID> getEndpointsUUIDsByOrgId(final String orgId) {
        String query = "SELECT id FROM Endpoint where orgId = :orgId ";
        return entityManager.createQuery(query, UUID.class)
            .setParameter("orgId", orgId)
            .getResultList();
    }

}
