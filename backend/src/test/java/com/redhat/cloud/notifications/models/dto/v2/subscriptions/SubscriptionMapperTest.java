package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.SubscriptionType;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class SubscriptionMapperTest {

    @Inject
    SubscriptionMapper subscriptionMapper;

    @Test
    void shouldMapEachSeverityToItsDTOCounterpart() {
        assertEquals(SeverityDTO.CRITICAL, subscriptionMapper.severityToSeverityDTO(Severity.CRITICAL));
        assertEquals(SeverityDTO.IMPORTANT, subscriptionMapper.severityToSeverityDTO(Severity.IMPORTANT));
        assertEquals(SeverityDTO.MODERATE, subscriptionMapper.severityToSeverityDTO(Severity.MODERATE));
        assertEquals(SeverityDTO.LOW, subscriptionMapper.severityToSeverityDTO(Severity.LOW));
        assertEquals(SeverityDTO.NONE, subscriptionMapper.severityToSeverityDTO(Severity.NONE));
    }

    @Test
    void shouldRoundTripEverySeverityDTOThroughSeverity() {
        for (SeverityDTO severityDTO : SeverityDTO.values()) {
            Severity severity = subscriptionMapper.severityDTOToSeverity(severityDTO);
            assertEquals(severityDTO, subscriptionMapper.severityToSeverityDTO(severity));
        }
    }

    @Test
    void shouldMapUndefinedSeverityToNull() {
        // Severity.UNDEFINED has no SeverityDTO counterpart and must never be surfaced by this API;
        // callers filter out the null this returns instead of exposing it.
        assertNull(subscriptionMapper.severityToSeverityDTO(Severity.UNDEFINED));
    }

    @Test
    void shouldRoundTripEverySubscriptionTypeThroughSubscriptionTypeDTO() {
        for (SubscriptionType subscriptionType : SubscriptionType.values()) {
            SubscriptionTypeDTO subscriptionTypeDTO = subscriptionMapper.subscriptionTypeToSubscriptionTypeDTO(subscriptionType);
            assertEquals(subscriptionType, subscriptionMapper.subscriptionTypeDTOToSubscriptionType(subscriptionTypeDTO));
        }
    }
}
