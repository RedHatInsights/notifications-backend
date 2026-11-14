package com.redhat.cloud.notifications.models.mappers.v3.endpoint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.properties.SystemSubscriptionPropertiesDTO;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
public class EndpointMapperTest {

    final EndpointMapper endpointMapper;

    final ObjectMapper mapper;

    public EndpointMapperTest(EndpointMapper endpointMapper, ObjectMapper objectMapper) {
        this.endpointMapper = endpointMapper;
        this.mapper = objectMapper;
    }

    @Test
    void testSystemSubscriptionIgnorePreferencesForcedFalseOnEntity() {
        final SystemSubscriptionPropertiesDTO dto = new SystemSubscriptionPropertiesDTO();
        dto.setOnlyAdmins(false);

        final SystemSubscriptionProperties entity = endpointMapper.systemToEntity(dto);

        assertFalse(entity.isIgnorePreferences(),
            "ignorePreferences must always be false on the entity when mapped from V3 DTO");
    }

    @Test
    void testSystemSubscriptionIgnorePreferencesNotExposedInV3DTO() {
        final SystemSubscriptionProperties entity = new SystemSubscriptionProperties();
        entity.setIgnorePreferences(true);
        entity.setOnlyAdmins(false);

        final SystemSubscriptionPropertiesDTO dto = endpointMapper.systemToDTO(entity);

        assertNotNull(dto);
        Assertions.assertFalse(dto.isOnlyAdmins());

        final JsonObject jo = mapper.convertValue(dto, JsonObject.class);
        assertFalse(jo.containsKey("ignore_preferences"));
    }
}
