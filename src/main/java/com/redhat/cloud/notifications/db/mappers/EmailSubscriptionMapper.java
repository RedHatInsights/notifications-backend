package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EndpointEmailSubscriptionEntity;
import com.redhat.cloud.notifications.db.entities.EndpointEmailSubscriptionEntityId;
import com.redhat.cloud.notifications.models.EmailSubscription;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.models.EmailSubscription.EmailSubscriptionType;

@ApplicationScoped
public class EmailSubscriptionMapper {

    public EndpointEmailSubscriptionEntity dtoToEntity(EmailSubscription dto) {
        EndpointEmailSubscriptionEntityId id = new EndpointEmailSubscriptionEntityId();
        id.accountId = dto.getAccountId();
        id.userId = dto.getUsername();
        id.bundleName = dto.getBundle();
        id.applicationName = dto.getApplication();
        if (dto.getType() != null) {
            id.subscriptionType = dto.getType().name();
        }
        EndpointEmailSubscriptionEntity entity = new EndpointEmailSubscriptionEntity();
        entity.id = id;
        return entity;
    }

    public EmailSubscription entityToDto(EndpointEmailSubscriptionEntity entity) {
        EmailSubscription dto = new EmailSubscription();
        if (entity.id != null) {
            dto.setAccountId(entity.id.accountId);
            dto.setUsername(entity.id.userId);
            dto.setBundle(entity.id.bundleName);
            dto.setApplication(entity.id.applicationName);
            if (entity.id.subscriptionType != null) {
                dto.setType(EmailSubscriptionType.valueOf(entity.id.subscriptionType));
            }
        }
        return dto;
    }
}
