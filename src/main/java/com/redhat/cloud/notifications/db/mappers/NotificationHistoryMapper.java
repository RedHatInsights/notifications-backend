package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EndpointEntity;
import com.redhat.cloud.notifications.db.entities.NotificationHistoryEntity;
import com.redhat.cloud.notifications.models.NotificationHistory;
import io.vertx.core.json.JsonObject;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class NotificationHistoryMapper {

    @Inject
    Mutiny.Session session;

    public NotificationHistoryEntity dtoToEntity(NotificationHistory dto) {
        NotificationHistoryEntity entity = new NotificationHistoryEntity();
        entity.accountId = dto.getTenant();
        if (dto.getEndpoint() != null && dto.getEndpoint().getId() != null) {
            entity.endpoint = session.getReference(EndpointEntity.class, dto.getEndpoint().getId());
        }
        entity.invocationTime = dto.getInvocationTime();
        entity.invocationResult = dto.isInvocationResult();
        entity.eventId = dto.getEventId();
        if (dto.getDetails() != null) {
            entity.details = new JsonObject(dto.getDetails()).encode();
        }
        return entity;
    }

    public NotificationHistory entityToDto(NotificationHistoryEntity entity) {
        NotificationHistory dto = new NotificationHistory();
        dto.setId(entity.id);
        if (entity.endpoint != null) {
            dto.setEndpointId(entity.endpoint.id);
        }
        dto.setInvocationTime(entity.invocationTime);
        dto.setInvocationResult(entity.invocationResult);
        dto.setEventId(entity.eventId);
        dto.setCreated(entity.created);
        return dto;
    }
}
