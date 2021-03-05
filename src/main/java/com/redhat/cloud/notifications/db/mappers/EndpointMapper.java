package com.redhat.cloud.notifications.db.mappers;

import com.redhat.cloud.notifications.db.entities.EndpointEntity;
import com.redhat.cloud.notifications.db.entities.EndpointWebhookEntity;
import com.redhat.cloud.notifications.models.Attributes;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.WebhookAttributes;
import io.vertx.core.json.Json;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.models.Endpoint.EndpointType;
import static com.redhat.cloud.notifications.models.WebhookAttributes.BasicAuthentication;
import static com.redhat.cloud.notifications.models.WebhookAttributes.HttpType;

@ApplicationScoped
public class EndpointMapper {

    public EndpointEntity dtoToEntity(Endpoint dto) {
        EndpointEntity entity = new EndpointEntity();
        entity.accountId = dto.getTenant();
        if (dto.getType() != null) {
            entity.endpointType = dto.getType().ordinal();
        }
        entity.enabled = dto.isEnabled();
        entity.name = dto.getName();
        entity.description = dto.getDescription();
        if (dto.getProperties() != null) {
            switch (dto.getType()) {
                case WEBHOOK:
                    EndpointWebhookEntity webhookEntity = buildWebhookEntity(dto.getProperties());
                    webhookEntity.endpoint = entity;
                    entity.webhook = webhookEntity;
                    break;
                case EMAIL_SUBSCRIPTION:
                case DEFAULT:
                default:
                    // Do nothing for now
                    break;
            }
        }
        return entity;
    }

    private EndpointWebhookEntity buildWebhookEntity(Attributes properties) {
        WebhookAttributes attr = (WebhookAttributes) properties;
        EndpointWebhookEntity entity = new EndpointWebhookEntity();
        entity.url = attr.getUrl();
        if (attr.getMethod() != null) {
            entity.method = attr.getMethod().name();
        }
        entity.disableSslVerification = attr.isDisableSSLVerification();
        entity.secretToken = attr.getSecretToken();
        if (attr.getBasicAuthentication() != null) {
            entity.basicAuthentication = Json.encode(attr.getBasicAuthentication());
        }
        return entity;
    }

    public Endpoint entityToDto(EndpointEntity entity) {
        Endpoint dto = new Endpoint();
        dto.setId(entity.id);
        dto.setTenant(entity.accountId);
        if (entity.endpointType != null) {
            dto.setType(EndpointType.values()[entity.endpointType]);
        }
        dto.setEnabled(entity.enabled);
        dto.setName(entity.name);
        dto.setDescription(entity.description);
        dto.setCreated(entity.created);
        dto.setUpdated(entity.updated);
        switch (dto.getType()) {
            case WEBHOOK:
                if (entity.webhook != null) {
                    dto.setProperties(buildWebhookAttributes(entity.webhook));
                }
                break;
            case EMAIL_SUBSCRIPTION:
            case DEFAULT:
            default:
                // Do nothing for now
                break;
        }
        return dto;
    }

    private WebhookAttributes buildWebhookAttributes(EndpointWebhookEntity webhookEntity) {
        WebhookAttributes attr = new WebhookAttributes();
        attr.setId(webhookEntity.id);
        attr.setUrl(webhookEntity.url);
        if (webhookEntity.method != null) {
            attr.setMethod(HttpType.valueOf(webhookEntity.method));
        }
        attr.setDisableSSLVerification(webhookEntity.disableSslVerification);
        attr.setSecretToken(webhookEntity.secretToken);
        if (webhookEntity.basicAuthentication != null) {
            attr.setBasicAuthentication(Json.decodeValue(webhookEntity.basicAuthentication, BasicAuthentication.class));
        }
        return attr;
    }
}
