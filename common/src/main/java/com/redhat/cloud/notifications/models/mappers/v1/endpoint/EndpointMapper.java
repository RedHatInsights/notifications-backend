package com.redhat.cloud.notifications.models.mappers.v1.endpoint;

import com.redhat.cloud.notifications.models.CamelProperties;
import com.redhat.cloud.notifications.models.Endpoint;
import com.redhat.cloud.notifications.models.EndpointProperties;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.WebhookProperties;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.EndpointDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.CamelPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.EndpointPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.SystemSubscriptionPropertiesDTO;
import com.redhat.cloud.notifications.models.dto.v1.endpoint.properties.WebhookPropertiesDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface EndpointMapper {
    /**
     * Maps an internal endpoint's entity into a DTO.
     * @param endpoint the internal entity to map.
     * @return the mapped DTO.
     */
    @Mapping(source = "properties", target = "properties", qualifiedByName = "mapEntityProperties")
    EndpointDTO toDTO(Endpoint endpoint);

    /**
     * Maps an endpoint's DTO to an internal entity.
     * @param endpoint the DTO to map.
     * @return the mapped internal entity.
     */
    @Mapping(source = "properties", target = "properties", qualifiedByName = "mapDTOProperties")
    @Mapping(target = "accountId", ignore = true)
    @Mapping(target = "behaviorGroupActions", ignore = true)
    @Mapping(target = "notificationHistories", ignore = true)
    @Mapping(target = "orgId", ignore = true)
    @Mapping(target = "updated", ignore = true)
    Endpoint toEntity(EndpointDTO endpoint);

    /**
     * Maps a camel properties' internal entity to a DTO.
     * @param camelProperties the internal entity to map.
     * @return the mapped DTO.
     */
    CamelPropertiesDTO camelToDTO(CamelProperties camelProperties);

    /**
     * Maps a camel properties' DTO into an internal entity.
     * @param camelPropertiesDTO the DTO to map.
     * @return the mapped internal entity.
     */
    @Mapping(target = "basicAuthenticationSourcesId", ignore = true)
    @Mapping(target = "bearerAuthentication", ignore = true)
    @Mapping(target = "bearerAuthenticationSourcesId", ignore = true)
    @Mapping(target = "endpoint", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "secretTokenSourcesId", ignore = true)
    CamelProperties camelToEntity(CamelPropertiesDTO camelPropertiesDTO);

    /**
     * Maps a system subscription properties' internal entity into a DTO.
     * @param systemSubscriptionProperties the internal entity to map.
     * @return the mapped DTO.
     */
    SystemSubscriptionPropertiesDTO systemToDTO(SystemSubscriptionProperties systemSubscriptionProperties);

    /**
     * Maps a system subscription properties' DTO into an internal entity.
     * @param systemSubscriptionPropertiesDTO the DTO to map.
     * @return thue mapped internal entity.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "endpoint", ignore = true)
    SystemSubscriptionProperties systemToEntity(SystemSubscriptionPropertiesDTO systemSubscriptionPropertiesDTO);

    /**
     * Maps a webhook properties' internal entity into a DTO.
     * @param webhookProperties the internal entity to map.
     * @return the mapped DTO.
     */
    WebhookPropertiesDTO webhookToDTO(WebhookProperties webhookProperties);

    /**
     * Maps a webhook properties DTO into an internal entity.
     * @param webhookPropertiesDTO the DTO to map.
     * @return the mapped internal entity.
     */
    @Mapping(target = "basicAuthenticationSourcesId", ignore = true)
    @Mapping(target = "bearerAuthenticationSourcesId", ignore = true)
    @Mapping(target = "endpoint", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "secretTokenSourcesId", ignore = true)
    WebhookProperties webhookToEntity(WebhookPropertiesDTO webhookPropertiesDTO);

    /**
     * Maps the internal endpoint properties' entity into one of the more specific classes that extend it, and then
     * maps them into an endpoint properties' DTO.
     * @param endpointProperties the internal entity to map.
     * @return the mapped DTO.
     */
    @Named("mapEntityProperties")
    default EndpointPropertiesDTO mapEntityProperties(final EndpointProperties endpointProperties) {
        if (endpointProperties == null) {
            return null;
        }

        if (endpointProperties instanceof CamelProperties properties) {
            return this.camelToDTO(properties);
        } else if (endpointProperties instanceof SystemSubscriptionProperties properties) {
            return this.systemToDTO(properties);
        } else if (endpointProperties instanceof WebhookProperties properties) {
            return this.webhookToDTO(properties);
        } else {
            throw new IllegalStateException("Invalid endpoint properties mapped to class");
        }
    }

    /**
     * Maps the generic endpoint properties' DTO into one of the more specific classes that extend it, and then maps
     * them into an endpoint properties entity.
     * @param endpointPropertiesDTO the DTO to map.
     * @return the mapped internal entity.
     */
    @Named("mapDTOProperties")
    default EndpointProperties mapDTOProperties(final EndpointPropertiesDTO endpointPropertiesDTO) {
        if (endpointPropertiesDTO == null) {
            return null;
        }

        if (endpointPropertiesDTO instanceof CamelPropertiesDTO properties) {
            return this.camelToEntity(properties);
        } else if (endpointPropertiesDTO instanceof SystemSubscriptionPropertiesDTO properties) {
            return this.systemToEntity(properties);
        } else if (endpointPropertiesDTO instanceof WebhookPropertiesDTO properties) {
            return this.webhookToEntity(properties);
        } else {
            throw new IllegalStateException("Invalid endpoint properties mapped to class");
        }
    }
}
