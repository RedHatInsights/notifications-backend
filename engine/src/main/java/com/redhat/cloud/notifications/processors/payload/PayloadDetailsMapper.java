package com.redhat.cloud.notifications.processors.payload;

import com.redhat.cloud.notifications.processors.payload.dto.v1.ReadPayloadDetailsDto;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface PayloadDetailsMapper {
    ReadPayloadDetailsDto toDto(PayloadDetails payloadDetails);
}
