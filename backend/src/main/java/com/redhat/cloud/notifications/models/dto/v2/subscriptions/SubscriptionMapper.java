package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.models.SubscriptionType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface SubscriptionMapper {

    // ANY_REMAINING keeps normal by-name matching for every constant that has a SeverityDTO
    // counterpart, and only catches the ones that don't (currently just UNDEFINED, which is
    // slated for removal from Severity) without naming it explicitly, so this needs no
    // follow-up change once that removal happens.
    @ValueMapping(source = MappingConstants.ANY_REMAINING, target = MappingConstants.THROW_EXCEPTION)
    SeverityDTO severityToSeverityDTO(Severity severity);

    Severity severityDTOToSeverity(SeverityDTO severityDTO);

    SubscriptionTypeDTO subscriptionTypeToSubscriptionTypeDTO(SubscriptionType subscriptionType);

    SubscriptionType subscriptionTypeDTOToSubscriptionType(SubscriptionTypeDTO subscriptionTypeDTO);

    // Maps the stable fields only; the resource layer fills in "subscriptions" itself, since that
    // one depends on the authenticated user's actual/default subscription state, not just the
    // EventType entity.
    @Mapping(source = "name", target = "eventType")
    @Mapping(target = "subscriptions", ignore = true)
    EventTypeSubscriptionDTO eventTypeToEventTypeSubscriptionDTO(EventType eventType);

    // Used automatically by eventTypeToEventTypeSubscriptionDTO() above for the
    // availableSeverities field. Sorts by severity rank (CRITICAL...NONE) so the wire format is
    // deterministic - a plain generated Set->List conversion wouldn't guarantee that order.
    default List<SeverityDTO> severitySetToSortedSeverityDTOList(Set<Severity> severities) {
        return severities.stream()
            .sorted()
            .map(this::severityToSeverityDTO)
            .collect(Collectors.toList());
    }
}
