package com.redhat.cloud.notifications.models.dto.v2.subscriptions;

import com.redhat.cloud.notifications.Severity;
import com.redhat.cloud.notifications.models.SubscriptionType;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;

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
}
