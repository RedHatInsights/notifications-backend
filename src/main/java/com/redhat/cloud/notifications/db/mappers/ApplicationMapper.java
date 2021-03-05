package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.ApplicationEntity;
import com.redhat.cloud.notifications.db.entities.BundleEntity;
import com.redhat.cloud.notifications.models.Application;
import org.hibernate.reactive.mutiny.Mutiny;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ApplicationMapper {

    @Inject
    Mutiny.Session session;

    public ApplicationEntity dtoToEntity(Application dto) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.name = dto.getName();
        entity.displayName = dto.getDisplay_name();
        if (dto.getBundleId() != null) {
            entity.bundle = session.getReference(BundleEntity.class, dto.getBundleId());
        }
        return entity;
    }

    public Application entityToDto(ApplicationEntity entity) {
        Application dto = entityToDtoWithoutTimestamps(entity);
        dto.setCreated(entity.created);
        dto.setUpdated(entity.updated);
        return dto;
    }

    public Application entityToDtoWithoutTimestamps(ApplicationEntity entity) {
        Application dto = new Application();
        dto.setId(entity.id);
        dto.setName(entity.name);
        dto.setDisplay_name(entity.displayName);
        if (entity.bundle != null) {
            dto.setBundleId(entity.bundle.id);
        }
        return dto;
    }
}

