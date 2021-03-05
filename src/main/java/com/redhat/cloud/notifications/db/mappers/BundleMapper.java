package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.BundleEntity;
import com.redhat.cloud.notifications.models.Bundle;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BundleMapper {

    public BundleEntity dtoToEntity(Bundle dto) {
        BundleEntity entity = new BundleEntity();
        entity.name = dto.getName();
        entity.displayName = dto.getDisplay_name();
        return entity;
    }

    public Bundle entityToDto(BundleEntity entity) {
        Bundle dto = new Bundle();
        dto.setId(entity.id);
        dto.setName(entity.name);
        dto.setDisplay_name(entity.displayName);
        dto.setCreated(entity.created);
        dto.setUpdated(entity.updated);
        return dto;
    }
}
