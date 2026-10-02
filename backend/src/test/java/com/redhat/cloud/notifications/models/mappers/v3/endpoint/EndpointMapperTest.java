package com.redhat.cloud.notifications.models.mappers.v3.endpoint;

import com.redhat.cloud.notifications.models.SystemSubscriptionProperties;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.EndpointMapper;
import com.redhat.cloud.notifications.models.dto.v3.endpoint.properties.SystemSubscriptionPropertiesDTO;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EndpointMapperTest {

    final EndpointMapper endpointMapper;

    public EndpointMapperTest(EndpointMapper endpointMapper) {
        this.endpointMapper = endpointMapper;
    }

    @Test
    void testSystemSubscriptionIgnorePreferencesForcedFalseOnEntity() {
        final SystemSubscriptionPropertiesDTO dto = new SystemSubscriptionPropertiesDTO();
        dto.setOnlyAdmins(false);

        final SystemSubscriptionProperties entity = this.endpointMapper.systemToEntity(dto);

        Assertions.assertFalse(entity.isIgnorePreferences(),
            "ignorePreferences must always be false on the entity when mapped from V3 DTO");
    }

    @Test
    void testSystemSubscriptionIgnorePreferencesNotExposedInV3DTO() {
        final SystemSubscriptionProperties entity = new SystemSubscriptionProperties();
        entity.setIgnorePreferences(true);
        entity.setOnlyAdmins(false);

        final SystemSubscriptionPropertiesDTO dto = this.endpointMapper.systemToDTO(entity);

        Assertions.assertNotNull(dto);
        Assertions.assertFalse(dto.isOnlyAdmins());
    }
}
