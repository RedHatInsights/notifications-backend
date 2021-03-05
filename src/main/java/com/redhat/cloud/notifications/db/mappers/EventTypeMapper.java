package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.ApplicationEntity;
import com.redhat.cloud.notifications.db.entities.EventTypeEntity;
import com.redhat.cloud.notifications.models.EventType;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class EventTypeMapper {

    @Inject
    Mutiny.Session session;

    @Inject
    ApplicationMapper applicationMapper;

    public EventTypeEntity dtoToEntity(EventType dto) {
        EventTypeEntity entity = new EventTypeEntity();
        entity.name = dto.getName();
        entity.displayName = dto.getDisplay_name();
        entity.description = dto.getDescription();
        if (dto.getApplication() != null && dto.getApplication().getId() != null) {
            entity.application = session.getReference(ApplicationEntity.class, dto.getApplication().getId());
        }
        return entity;
    }

    public EventType entityToDto(EventTypeEntity entity) {
        EventType dto = new EventType();
        dto.setId(entity.id);
        dto.setName(entity.name);
        dto.setDisplay_name(entity.displayName);
        dto.setDescription(entity.description);
        if (entity.application != null) {
            dto.setApplication(applicationMapper.entityToDtoWithoutTimestamps(entity.application));
        }
        return dto;
    }
}
