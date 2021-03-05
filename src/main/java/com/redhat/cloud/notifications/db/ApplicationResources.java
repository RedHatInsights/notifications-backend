package com.redhat.cloud.notifications.db;

import com.redhat.cloud.notifications.db.entities.ApplicationEntity;
import com.redhat.cloud.notifications.db.entities.EventTypeEntity;
import com.redhat.cloud.notifications.db.mappers.ApplicationMapper;
import com.redhat.cloud.notifications.db.mappers.EventTypeMapper;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.EventType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class ApplicationResources {

    @Inject
    Mutiny.Session session;

    @Inject
    ApplicationMapper applicationMapper;

    @Inject
    EventTypeMapper eventTypeMapper;

    public Uni<Application> createApplication(Application app) {
        // Return filled with id
        return Uni.createFrom().item(() -> applicationMapper.dtoToEntity(app))
                .flatMap(applicationEntity -> session.persist(applicationEntity)
                        .replaceWith(applicationEntity))
                .call(() -> session.flush())
                .onItem().transform(applicationMapper::entityToDto);
    }

    public Uni<Boolean> deleteApplication(UUID applicationId) {
        String query = "DELETE FROM ApplicationEntity WHERE id = :applicationId";
        return session.createQuery(query)
                .setParameter("applicationId", applicationId)
                .executeUpdate()
                .call(() -> session.flush())
                .onItem().transform(rowCount -> rowCount > 0);
    }

    public Uni<EventType> addEventTypeToApplication(UUID applicationId, EventType type) {
        // FIXME It should be possible to avoid querying the app before the event type insert
        return session.find(ApplicationEntity.class, applicationId)
                .onItem().transform(applicationEntity -> {
                    EventTypeEntity eventTypeEntity = eventTypeMapper.dtoToEntity(type);
                    eventTypeEntity.application = applicationEntity;
                    return eventTypeEntity;
                })
                .flatMap(eventTypeEntity -> session.persist(eventTypeEntity)
                        .replaceWith(eventTypeEntity)
                )
                .call(() -> session.flush())
                .onItem().transform(eventTypeMapper::entityToDto);
    }

    public Multi<Application> getApplications(String bundleName) {
        String query = "FROM ApplicationEntity WHERE bundle.name = :bundleName";
        return session.createQuery(query, ApplicationEntity.class)
                .setParameter("bundleName", bundleName)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(applicationMapper::entityToDto);
    }

    public Uni<Application> getApplication(UUID applicationId) {
        return session.find(ApplicationEntity.class, applicationId)
                .onItem().ifNotNull().transform(applicationMapper::entityToDto);
    }

    public Uni<Application> getApplication(String bundleName, String applicationName) {
        String query = "FROM ApplicationEntity WHERE bundle.name = :bundleName AND name = :applicationName";
        return session.createQuery(query, ApplicationEntity.class)
                .setParameter("bundleName", bundleName)
                .setParameter("applicationName", applicationName)
                .getSingleResultOrNull()
                .onItem().ifNotNull().transform(applicationMapper::entityToDto);
    }

    public Multi<EventType> getEventTypes(UUID applicationId) {
        String query = "FROM EventTypeEntity WHERE application.id = :applicationId";
        return session.createQuery(query, EventTypeEntity.class)
                .setParameter("applicationId", applicationId)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(eventTypeMapper::entityToDto);
    }

    public Uni<Boolean> deleteEventTypeById(UUID eventTypeId) {
        String query = "DELETE FROM EventTypeEntity WHERE id = :eventTypeId";
        return session.createQuery(query)
                .setParameter("eventTypeId", eventTypeId)
                .executeUpdate()
                .call(() -> session.flush())
                .onItem().transform(rowCount -> rowCount > 0);
    }

    public Multi<EventType> getEventTypes(Query limiter, Set<UUID> applicationId, UUID bundleId) {
        String query = "SELECT e FROM EventTypeEntity e LEFT JOIN FETCH e.application";

        List<String> conditions = new ArrayList<>();
        if (applicationId != null && applicationId.size() > 0) {
            conditions.add("e.application.id IN (:applicationId)");
        }
        if (bundleId != null) {
            conditions.add("e.application.bundle.id = :bundleId");
        }
        if (conditions.size() > 0) {
            query += " WHERE " + String.join(" AND ", conditions);
        }

        if (limiter != null) {
            query = limiter.getModifiedQuery(query);
        }

        Mutiny.Query<EventTypeEntity> mutinyQuery = session.createQuery(query, EventTypeEntity.class);
        if (applicationId != null && applicationId.size() > 0) {
            mutinyQuery = mutinyQuery.setParameter("applicationId", applicationId);
        }
        if (bundleId != null) {
            mutinyQuery = mutinyQuery.setParameter("bundleId", bundleId);
        }

        if (limiter != null && limiter.getLimit() != null && limiter.getLimit().getLimit() > 0) {
            mutinyQuery = mutinyQuery.setMaxResults(limiter.getLimit().getLimit())
                    .setFirstResult(limiter.getLimit().getOffset());
        }

        return mutinyQuery.getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(eventTypeMapper::entityToDto);
    }

    public Multi<EventType> getEventTypesByEndpointId(@NotNull String accountId, @NotNull UUID endpointId) {
        String query = "SELECT e FROM EventTypeEntity e LEFT JOIN FETCH e.application JOIN e.targets t " +
                "WHERE t.id.accountId = :accountId AND t.endpoint.id = :endpointId";
        return session.createQuery(query, EventTypeEntity.class)
                .setParameter("accountId", accountId)
                .setParameter("endpointId", endpointId)
                .getResultList()
                .onItem().transformToMulti(Multi.createFrom()::iterable)
                .onItem().transform(eventTypeMapper::entityToDto);
    }
}
