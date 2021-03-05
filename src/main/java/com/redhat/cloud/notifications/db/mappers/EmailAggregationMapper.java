package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EmailAggregationEntity;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonObject;

import javax.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailAggregationMapper {

    public EmailAggregationEntity dtoToEntity(EmailAggregation dto) {
        EmailAggregationEntity entity = new EmailAggregationEntity();
        entity.accountId = dto.getAccountId();
        entity.bundleName = dto.getBundle();
        entity.applicationName = dto.getApplication();
        if (dto.getPayload() != null) {
            entity.payload = dto.getPayload().encode();
        }
        return entity;
    }

    public EmailAggregation entityToDto(EmailAggregationEntity entity) {
        EmailAggregation dto = new EmailAggregation();
        dto.setId(entity.id);
        dto.setAccountId(entity.accountId);
        dto.setBundle(entity.bundleName);
        dto.setApplication(entity.applicationName);
        if (entity.payload != null) {
            dto.setPayload(new JsonObject(entity.payload));
        }
        dto.setCreated(entity.created);
        return dto;
    }
}
